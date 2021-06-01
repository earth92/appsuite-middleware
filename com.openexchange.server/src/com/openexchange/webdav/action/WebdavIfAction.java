/*
 * @copyright Copyright (c) OX Software GmbH, Germany <info@open-xchange.com>
 * @license AGPL-3.0
 *
 * This code is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OX App Suite.  If not, see <https://www.gnu.org/licenses/agpl-3.0.txt>.
 * 
 * Any use of the work other than as authorized under this license or copyright law is prohibited.
 *
 */

package com.openexchange.webdav.action;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import javax.servlet.http.HttpServletResponse;
import com.openexchange.webdav.action.behaviour.BehaviourLookup;
import com.openexchange.webdav.action.ifheader.IfHeader;
import com.openexchange.webdav.action.ifheader.IfHeaderApply;
import com.openexchange.webdav.action.ifheader.IfHeaderEntity;
import com.openexchange.webdav.action.ifheader.IfHeaderList;
import com.openexchange.webdav.action.ifheader.IfHeaderParseException;
import com.openexchange.webdav.action.ifheader.StandardIfHeaderApply;
import com.openexchange.webdav.loader.LoadingHints;
import com.openexchange.webdav.protocol.Protocol;
import com.openexchange.webdav.protocol.WebdavCollection;
import com.openexchange.webdav.protocol.WebdavLock;
import com.openexchange.webdav.protocol.WebdavLock.Scope;
import com.openexchange.webdav.protocol.WebdavProtocolException;
import com.openexchange.webdav.protocol.WebdavResource;

public class WebdavIfAction extends AbstractAction {

	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(WebdavIfAction.class);

    private static final IfHeaderApply STANDARD_APPLY = new StandardIfHeaderApply();

	private int defaultDepth;
	private boolean checkSourceLocks;
	private boolean checkDestinationLocks;
    private boolean ignoreSharedLocks;

    public WebdavIfAction() {
        this(false, false);
    }

    public WebdavIfAction(boolean checkSourceLocks, boolean checkDestinationLocks) {
        this(0, checkSourceLocks, checkDestinationLocks);
    }

    public WebdavIfAction(int defaultDepth, boolean checkSourceLocks, boolean checkDestinationLocks) {
        this(defaultDepth, checkSourceLocks, checkDestinationLocks, false);
	}

    /**
     * Initializes a new {@link WebdavIfAction}.
     * 
     * @param defaultDepth The default depth to assume unless specified in request header
     * @param checkSourceLocks <code>true</code> to check locks on the targeted resource, <code>false</code>, otherwise
     * @param checkDestinationLocks <code>true</code> to check locks on resource targeted via destination header, <code>false</code>, otherwise
     * @param ignoreSharedLocks <code>true</code> to ignore locks with {@link Scope#SHARED_LITERAL}, <code>false</code>, otherwise
     */
    public WebdavIfAction(int defaultDepth, boolean checkSourceLocks, boolean checkDestinationLocks, boolean ignoreSharedLocks) {
        super();
        this.defaultDepth = defaultDepth;
        this.checkSourceLocks = checkSourceLocks;
        this.checkDestinationLocks = checkDestinationLocks;
        this.ignoreSharedLocks = ignoreSharedLocks;
    }

    /**
     * Checks the supplied WebDAV request, without yielding to the next chained action.
     *
     * @param req The request to check
     * @param rememberMentionedLocks <code>true</code> to remember mentioned locks, <code>false</code>, otherwise
     */
    public void check(WebdavRequest req, boolean rememberMentionedLocks) throws WebdavProtocolException {
        int depth = getDepth(req);
        IfHeader ifHeader;
        try {
            ifHeader = req.getIfHeader();
        } catch (IfHeaderParseException e) {
            LOG.trace("", e);
            throw WebdavProtocolException.generalError(e, req.getUrl(), HttpServletResponse.SC_PRECONDITION_FAILED);
        }
        if (null != ifHeader) {
            if (rememberMentionedLocks) {
                rememberMentionedLocks(req, ifHeader);
            }
            checkIfs(ifHeader, req, depth);
        }
        List<LoadingHints> lockHints = new ArrayList<LoadingHints>();
        if (checkSourceLocks) {
            lockHints.add(preloadSourceLocks(req, depth));
        }
        if (checkDestinationLocks) {
            lockHints.add(preloadDestinationLocks(req));
        }

        preLoad(lockHints);

        if (checkSourceLocks) {
            checkNeededLocks(ifHeader, req, depth);
        }
        if (checkDestinationLocks || req.getResource().isLockNull()) {
            checkDestinationLocks(ifHeader, req);
        }
    }

    @Override
    public void perform(final WebdavRequest req, final WebdavResponse res) throws WebdavProtocolException {
        check(req, true);
		yield(req,res);
	}

    private void rememberMentionedLocks(WebdavRequest req, IfHeader ifHeader) {
        List<String> mentionedLocks = new LinkedList<String>();
        for(final IfHeaderList list : ifHeader.getLists()) {
            for(final IfHeaderEntity entity : list) {
                if (entity.isLockToken()) {
                    mentionedLocks.add(entity.getPayload());
                }
            }
        }
        req.getUserInfo().put("mentionedLocks", mentionedLocks);
    }

    private LoadingHints preloadDestinationLocks(final WebdavRequest req) {
		final LoadingHints loadingHints = new LoadingHints();
		loadingHints.setUrl(req.getDestinationUrl());
		loadingHints.setDepth(0);
		loadingHints.setProps(LoadingHints.Property.NONE);
		loadingHints.loadLocks(true);
		return loadingHints;
	}

	private LoadingHints preloadSourceLocks(final WebdavRequest req, final int depth) {
		final LoadingHints loadingHints = new LoadingHints();
		loadingHints.setUrl(req.getUrl());
		loadingHints.setDepth(depth);
		loadingHints.setProps(LoadingHints.Property.NONE);
		loadingHints.loadLocks(true);
		return loadingHints;
	}

	private void checkDestinationLocks(final IfHeader ifHeader, final WebdavRequest req) throws WebdavProtocolException {
		if (null == req.getDestinationUrl()) {
			return;
		}


		final WebdavResource destination = req.getDestination();
		if (null == destination) {
			return;
		}

		final Set<String> locks = new HashSet<String>();

		for(final WebdavLock lock : destination.getLocks()) {
			locks.add(lock.getToken());
		}
		removeProvidedLocks(ifHeader,locks);

		if (!locks.isEmpty()) {
			throw WebdavProtocolException.Code.GENERAL_ERROR.create(req.getUrl(), Protocol.SC_LOCKED);
		}
	}

	private void checkNeededLocks(final IfHeader ifHeader, final WebdavRequest req, final int depth) throws WebdavProtocolException {
		final WebdavResource res = req.getResource();
		Iterable<WebdavResource> iter = null;
		if (res.isCollection()) {
			iter = res.toCollection().toIterable(depth);
		} else {
			iter = Collections.emptyList();
		}

		final Set<String> neededLocks = new HashSet<String>();

		for(final WebdavResource resource : iter) {
            addLocks(neededLocks, resource, ignoreSharedLocks);
		}

        addLocks(neededLocks, res, ignoreSharedLocks);

		removeProvidedLocks(ifHeader,neededLocks);

		if (!neededLocks.isEmpty()) {
			throw WebdavProtocolException.Code.GENERAL_ERROR.create(req.getUrl(), Protocol.SC_LOCKED);
		}
	}

	private void removeProvidedLocks(final IfHeader ifHeader, final Set<String> neededLocks) {
		if (null == ifHeader) {
			return;
		}
		for(final IfHeaderList list : ifHeader.getLists()) {
			for(final IfHeaderEntity entity : list) {
				if (entity.isLockToken()) {
					neededLocks.remove(entity.getPayload());
				}
			}
		}
	}

    private static void addLocks(final Set<String> neededLocks, final WebdavResource res, boolean ignoreSharedLocks) throws WebdavProtocolException {
		for(final WebdavLock lock : res.getLocks()) {
            if (ignoreSharedLocks && Scope.SHARED_LITERAL.equals(lock.getScope())) {
                continue;
            }
			neededLocks.add(lock.getToken());
		}
	}

	private void checkIfs(final IfHeader ifHeader, final WebdavRequest req, final int depth) throws WebdavProtocolException {
		final LoadingHints loadingHints = new LoadingHints();
		loadingHints.setUrl(req.getUrl());
		loadingHints.setDepth(depth);
		loadingHints.setProps(LoadingHints.Property.SOME);
		loadingHints.addProperty("DAV:", "getetag");
		loadingHints.loadLocks(true);
		preLoad(loadingHints);
		final WebdavResource res = req.getResource();
		Iterable<WebdavResource> iter = null;
		if (res.isCollection()) {
			iter = res.toCollection().toIterable(depth);
		} else {
			iter = Collections.emptyList();
		}

		for(final WebdavResource resource : iter) {
			if ( !checkList(ifHeader, resource, req) ) {
				throw WebdavProtocolException.Code.GENERAL_ERROR.create(req.getUrl(), HttpServletResponse.SC_PRECONDITION_FAILED);
			}
		}

		if ( checkList(ifHeader, res, req) ) {
			return ;
		}

		throw WebdavProtocolException.Code.GENERAL_ERROR.create(req.getUrl(), HttpServletResponse.SC_PRECONDITION_FAILED);
	}

	private boolean checkList(final IfHeader ifHeader, final WebdavResource resource, final WebdavRequest req) throws WebdavProtocolException {
		final List<IfHeaderList> relevant = ifHeader.getRelevant(req.getURLPrefix()+resource.getUrl());
		for(final IfHeaderList list : relevant) {
			if (matches(list, resource)) {
				return true;
			}
		}
		return relevant.isEmpty();
	}

	private boolean matches(final IfHeaderList list, final WebdavResource resource) throws WebdavProtocolException {
		for(final IfHeaderEntity entity : list) {
		    IfHeaderApply apply = getApply();
		    if (!apply.matches(entity, resource)) {
		        return false;
		    }
		}
		return true;
	}

	private IfHeaderApply getApply() {
	    IfHeaderApply apply = BehaviourLookup.getInstance().get(IfHeaderApply.class);
	    if (apply != null) {
	        return apply;
	    }
	    return STANDARD_APPLY;
	}

	private int getDepth(final WebdavRequest req) throws WebdavProtocolException {
		if (!req.getResource().isCollection()) {
			return 0;
		}
		final String depth = req.getHeader("Depth");
		return depth == null ? defaultDepth : depth.equalsIgnoreCase("Infinity") ? WebdavCollection.INFINITY : Integer.parseInt(depth);
	}

	public void setDefaultDepth(final int i) {
		this.defaultDepth = i;
	}

	public void checkSourceLocks(final boolean b) {
		this.checkSourceLocks = b;
	}

	public void checkDestinationLocks(final boolean b) {
		this.checkDestinationLocks = b;
    }

}
