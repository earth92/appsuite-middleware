/*
 *
 *    OPEN-XCHANGE legal information
 *
 *    All intellectual property rights in the Software are protected by
 *    international copyright laws.
 *
 *
 *    In some countries OX, OX Open-Xchange, open xchange and OXtender
 *    as well as the corresponding Logos OX Open-Xchange and OX are registered
 *    trademarks of the OX Software GmbH group of companies.
 *    The use of the Logos is not covered by the GNU General Public License.
 *    Instead, you are allowed to use these Logos according to the terms and
 *    conditions of the Creative Commons License, Version 2.5, Attribution,
 *    Non-commercial, ShareAlike, and the interpretation of the term
 *    Non-commercial applicable to the aforementioned license is published
 *    on the web site http://www.open-xchange.com/EN/legal/index.html.
 *
 *    Please make sure that third-party modules and libraries are used
 *    according to their respective licenses.
 *
 *    Any modifications to this package must retain all copyright notices
 *    of the original copyright holder(s) for the original code used.
 *
 *    After any such modifications, the original and derivative code shall remain
 *    under the copyright of the copyright holder(s) and/or original author(s)per
 *    the Attribution and Assignment Agreement that can be located at
 *    http://www.open-xchange.com/EN/developer/. The contributing author shall be
 *    given Attribution for the derivative code and a license granting use.
 *
 *     Copyright (C) 2016-2020 OX Software GmbH
 *     Mail: info@open-xchange.com
 *
 *
 *     This program is free software; you can redistribute it and/or modify it
 *     under the terms of the GNU General Public License, Version 2 as published
 *     by the Free Software Foundation.
 *
 *     This program is distributed in the hope that it will be useful, but
 *     WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *     or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 *     for more details.
 *
 *     You should have received a copy of the GNU General Public License along
 *     with this program; if not, write to the Free Software Foundation, Inc., 59
 *     Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 */

package com.openexchange.chronos.itip.generators;

import org.apache.commons.text.StringEscapeUtils;
import com.openexchange.chronos.ParticipationStatus;
import com.openexchange.chronos.compat.ShownAsTransparency;

/**
 * {@link HTMLWrapper}
 *
 * @author <a href="mailto:francisco.laguna@open-xchange.com">Francisco Laguna</a>
 */
public class HTMLWrapper extends PassthroughWrapper {

    @Override
    public String original(Object argument) {
        return wrap("original", argument);
    }

    @Override
    public String participant(Object argument) {
        return wrap("person", argument);
    }

    @Override
    public String state(Object argument, ParticipationStatus status) {
        return wrap("status " + getName(status), argument);
    }

    private String getName(ParticipationStatus status) {
        if (ParticipationStatus.ACCEPTED.matches(status)) {
            return "accepted";
        } else if (ParticipationStatus.DECLINED.matches(status)) {
            return "declined";
        } else if (ParticipationStatus.TENTATIVE.matches(status)) {
            return "tentative";
        } else {
            return "none";
        }
    }

    @Override
    public String updated(Object argument) {
        return wrap("updated", argument);
    }

    @Override
    public String emphasiszed(Object argument) {
        if (argument == null) {
            return "";
        }
        return "<em>" + escapeHtml(argument.toString()) + "</em>";
    }

    @Override
    public String shownAs(Object argument, ShownAsTransparency shownAs) {
        return wrap("shown_as_label " + shownAsCssClass(shownAs), argument);
    }

    private String shownAsCssClass(ShownAsTransparency shownAs) {
        if (null == shownAs) {
            return "unknown";
        }
        switch (shownAs) {
            case RESERVED:
                return "reserved";
            case TEMPORARY:
                return "temporary";
            case ABSENT:
                return "absent";
            case FREE:
                return "free";
            default:
                return "unknown";
        }
    }

    private String wrap(String string, Object argument) {
        if (argument == null) {
            return "";
        }
        return "<span class='" + string + "'>" + escapeHtml(argument.toString()) + "</span>";
    }

    @Override
    public String reference(Object argument) {
        if (argument == null) {
            return "";
        }
        String string = escapeHtml(argument.toString());
        return "<a href=\"" + string + "\">" + string + "</a>";
    }

    private String escapeHtml(String string) {
        return StringEscapeUtils.escapeHtml4(string);
    }

    @Override
    public String italic(Object argument) {
        if (argument == null) {
            return "";
        }
        return "<i>" + escapeHtml(argument.toString()) + "</i>";
    }

    @Override
    public String getFormat() {
        return "html";
    }

}
