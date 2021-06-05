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

package com.openexchange.ajax.requesthandler.oauth;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openexchange.ajax.requesthandler.AJAXActionService;
import com.openexchange.ajax.requesthandler.AJAXRequestData;
import com.openexchange.ajax.requesthandler.AbstractAJAXActionAnnotationProcessor;
import com.openexchange.exception.OXException;
import com.openexchange.oauth.provider.exceptions.OAuthInsufficientScopeException;
import com.openexchange.oauth.provider.resourceserver.OAuthAccess;
import com.openexchange.oauth.provider.resourceserver.annotations.OAuthAction;
import com.openexchange.oauth.provider.resourceserver.annotations.OAuthScopeCheck;
import com.openexchange.tools.session.ServerSession;


/**
 * {@link OAuthAnnotationProcessor}
 *
 * @author <a href="mailto:steffen.templin@open-xchange.com">Steffen Templin</a>
 * @since v7.8.0
 */
public class OAuthAnnotationProcessor extends AbstractAJAXActionAnnotationProcessor<OAuthAction> {

    private static final Logger LOG = LoggerFactory.getLogger(OAuthAnnotationProcessor.class);

    @Override
    protected Class<OAuthAction> getAnnotation() {
        return OAuthAction.class;
    }

    @Override
    protected void doProcess(OAuthAction annotation, AJAXActionService action, AJAXRequestData requestData, ServerSession session) throws OXException {
        OAuthAccess oAuthAccess = requestData.getProperty(OAuthConstants.PARAM_OAUTH_ACCESS);
        if (oAuthAccess == null) {
            return;
        }

        OAuthAction oAuthAction = action.getClass().getAnnotation(OAuthAction.class);
        String requiredScope = oAuthAction.value();
        if (OAuthAction.GRANT_ALL.equals(requiredScope)) {
            return;
        } else if (OAuthAction.CUSTOM.equals(requiredScope)) {
            for (Method method : action.getClass().getMethods()) {
                if (method.isAnnotationPresent(OAuthScopeCheck.class)) {
                    if (hasScopeCheckSignature(method)) {
                        try {
                            if (((Boolean) method.invoke(action, requestData, session, oAuthAccess)).booleanValue()) {
                                return;
                            }
                        } catch (InvocationTargetException e) {
                            Throwable cause = e.getCause();
                            if (cause instanceof OXException) {
                                throw (OXException) cause;
                            }

                            throw new OXException(cause);
                        } catch (IllegalAccessException | IllegalArgumentException e) {
                            LOG.error("Could not check scope", e);
                            throw new OXException(e);
                        }
                    } else {
                        LOG.warn("Method ''{}.{}'' is annotated with @OAuthScopeCheck but its signature is invalid!", action.getClass(), method.getName());
                    }
                }
            }

            throw new OAuthInsufficientScopeException();
        } else {
            if (!oAuthAccess.getScope().has(requiredScope)) {
                throw new OAuthInsufficientScopeException(requiredScope);
            }
        }
    }

    private static boolean hasScopeCheckSignature(Method method) {
        if (Modifier.isPublic(method.getModifiers()) && method.getReturnType().isAssignableFrom(boolean.class)) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length == 3) {
                return parameterTypes[0].isAssignableFrom(AJAXRequestData.class) &&
                       parameterTypes[1].isAssignableFrom(ServerSession.class) &&
                       parameterTypes[2].isAssignableFrom(OAuthAccess.class);
            }
        }

        return false;
    }

}
