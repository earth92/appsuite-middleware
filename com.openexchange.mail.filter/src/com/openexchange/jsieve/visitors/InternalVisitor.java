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

package com.openexchange.jsieve.visitors;

import java.util.ArrayList;
import org.apache.jsieve.NumberArgument;
import org.apache.jsieve.SieveException;
import org.apache.jsieve.TagArgument;
import org.apache.jsieve.parser.generated.ASTargument;
import org.apache.jsieve.parser.generated.ASTarguments;
import org.apache.jsieve.parser.generated.ASTblock;
import org.apache.jsieve.parser.generated.ASTcommand;
import org.apache.jsieve.parser.generated.ASTcommands;
import org.apache.jsieve.parser.generated.ASTstart;
import org.apache.jsieve.parser.generated.ASTstring;
import org.apache.jsieve.parser.generated.ASTstring_list;
import org.apache.jsieve.parser.generated.ASTtest;
import org.apache.jsieve.parser.generated.ASTtest_list;
import org.apache.jsieve.parser.generated.Node;
import org.apache.jsieve.parser.generated.SieveParserVisitor;
import org.apache.jsieve.parser.generated.SimpleNode;
import com.openexchange.jsieve.commands.ActionCommand;
import com.openexchange.jsieve.commands.Rule;
import com.openexchange.jsieve.commands.TestCommand;
import com.openexchange.jsieve.commands.test.ITestCommand;
import com.openexchange.jsieve.registry.TestCommandRegistry;
import com.openexchange.mailfilter.services.Services;

/**
 * This class is used to convert the JJTree Objects into the internal
 * representation via the visitor pattern
 *
 * @author d7
 * @author <a href="mailto:ioannis.chouklis@open-xchange.com">Ioannis Chouklis</a>
 */
@SuppressWarnings(value = { "unchecked" })
public class InternalVisitor implements SieveParserVisitor {

    private boolean commented;

    /**
     * Initialises a new {@link InternalVisitor}.
     */
    public InternalVisitor() {
        super();
    }

    @Override
    public Object visit(final SimpleNode node, final Object data) throws SieveException {
        return null;
    }

    @Override
    public Object visit(final ASTstart node, final Object data) throws SieveException {
        if (data instanceof Boolean) {
            final Boolean value = (Boolean) data;
            commented = value.booleanValue();
        }
        return VisitorUtil.visitChildren(node, null, this);
    }

    @Override
    public Object visit(final ASTcommands node, final Object data) throws SieveException {
        if (null != data) {
            return VisitorUtil.visitChildren(node, data, this);
        } else {
            return VisitorUtil.visitChildren(node, new ArrayList<Rule>(node.jjtGetNumChildren()), this);
        }
    }

    @Override
    public Object visit(final ASTcommand node, final Object data) throws SieveException {
        final String name = node.getName();
        NodeType nodeType;
        try {
            nodeType = NodeType.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            nodeType = NodeType.OTHER;
        }
        try {
            nodeType.parse(node, data, commented, this);
        } catch (SieveException e) {
            ((ArrayList<Rule>) data).add(new Rule(commented, node.getCoordinate().getStartLineNumber(), node.getCoordinate().getEndLineNumber(), e.getMessage()));
        }
        return data;
    }

    @Override
    public Object visit(final ASTblock node, final Object data) throws SieveException {
        return VisitorUtil.visitChildren(node, new ArrayList<ActionCommand>(), this);
    }

    @Override
    public Object visit(final ASTarguments node, final Object data) throws SieveException {
        return VisitorUtil.visitChildren(node, new ArrayList<Object>(), this);
    }

    @Override
    public Object visit(final ASTargument node, final Object data) throws SieveException {
        if (0 < node.jjtGetNumChildren()) {
            final Object visitChildren = VisitorUtil.visitChildren(node, data, this);
            if (visitChildren instanceof ArrayList) {
                final ArrayList<String> list = (ArrayList<String>) visitChildren;
                ((ArrayList<ArrayList<String>>) data).add(list);
                return data;
            }
            return visitChildren;
        } else {
            final Object value = node.getValue();
            if (value instanceof TagArgument || value instanceof NumberArgument) {
                ((ArrayList<Object>) data).add(value);
                return data;
            } else {
                final String string = value.toString();
                ((ArrayList<Object>) data).add(string);
                return data;
            }
        }
    }

    @Override
    public Object visit(final ASTtest node, final Object data) throws SieveException {
        final String name = node.getName();
        // now use registry:
        TestCommandRegistry testCommandRegistry = Services.getService(TestCommandRegistry.class);
        for (final ITestCommand command : testCommandRegistry.getCommands()) {
            TestCommand testCommand = VisitorUtil.visit(node, data, name, command, this);
            if (null != testCommand) {
                return testCommand;
            }
        }

        throw new SieveException("Found not known test name: " + name + " in line " + node.getCoordinate().getStartLineNumber());
    }

    @Override
    public Object visit(final ASTtest_list node, final Object data) throws SieveException {
        final ArrayList<TestCommand> list = new ArrayList<TestCommand>();
        for (int i = 0; i < node.jjtGetNumChildren(); i++) {
            final Node child = node.jjtGetChild(i);
            final TestCommand test = (TestCommand) child.jjtAccept(this, null);
            list.add(test);
        }
        ((ArrayList<Object>) data).add(list);
        return data;
    }

    @Override
    public Object visit(final ASTstring node, final Object data) throws SieveException {
        final Object value = node.getValue();
        final String string = value.toString();
        if (string.charAt(0) == '\"') {
            return string.substring(1, string.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        } else {
            final int linebreak = string.indexOf("\n");
            // Here we have to cut 5 chars from the end, because a linebreak in a sieve script
            // consists of CRLF and the "text:" tag finishes with an empty "."after the text.
            return string.substring(linebreak + 1, string.length() - 5).replace("\\\"", "\"").replace("\\\\", "\\");
        }
    }

    @Override
    public Object visit(final ASTstring_list node, final Object data) throws SieveException {
        final ArrayList<String> list = new ArrayList<String>();
        for (int i = 0; i < node.jjtGetNumChildren(); i++) {
            final Node child = node.jjtGetChild(i);
            final String string = (String) child.jjtAccept(this, data);
            list.add(string);
        }
        return list;
    }
}
