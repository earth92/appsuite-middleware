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

package com.openexchange.jsieve.commands;

import java.util.ArrayList;

// Comparable<Rule> allows only a Rule object for compare
public class Rule implements Comparable<Rule> {

    private RuleComment ruleComment;

    private boolean commented;

    private int linenumber = -1;

    private int endlinenumber = -1;

    private int position = -1;

    private String text;

    private String errormsg;

    private ArrayList<Command> commands;

    public Rule() {
        commands = new ArrayList<Command>();
    }

    public Rule(final RuleComment name, final ArrayList<Command> commands) {
        super();
        ruleComment = name;
        this.commands = commands;
    }

    public Rule(final ArrayList<Command> commands, final int linenumber) {
        super();
        this.commands = commands;
        this.linenumber = linenumber;
    }

    public Rule(final ArrayList<Command> commands, final int linenumber, final int endlinenumber, final boolean commented) {
        super();
        this.commands = commands;
        this.linenumber = linenumber;
        this.endlinenumber = endlinenumber;
        this.commented = commented;
    }

    /**
     * @param commented
     * @param linenumber
     * @param text
     * @param errormsg
     */
    public Rule(final boolean commented, final int linenumber, final int endlinenumber, final String errormsg) {
        super();
        this.commented = commented;
        this.linenumber = linenumber;
        this.endlinenumber = endlinenumber;
        this.errormsg = errormsg;
    }

    public Rule(final RuleComment name, final ArrayList<Command> command, final boolean commented) {
        super();
        ruleComment = name;
        commands = command;
        this.commented = commented;
    }

    public Rule(final RuleComment name, final ArrayList<Command> command, final int linenumber, final boolean commented) {
        super();
        ruleComment = name;
        commands = command;
        this.linenumber = linenumber;
        this.commented = commented;
    }

    public final RuleComment getRuleComment() {
        return ruleComment;
    }

    /**
     * @param o
     * @return
     * @see java.util.ArrayList#add(java.lang.Object)
     */
    public boolean addCommand(final Command o) {
        return commands.add(o);
    }

    /**
     * @param o
     * @return
     * @see java.util.ArrayList#remove(java.lang.Object)
     */
    public boolean removeCommand(final Object o) {
        return commands.remove(o);
    }

    public final ArrayList<Command> getCommands() {
        if (commands == null) {
            commands = new ArrayList<Command>();
        }
        return commands;
    }

    /**
     * A convenience method to get the require command if one is contained
     * 
     * @return the require command or null if none is contained
     */
    public final RequireCommand getRequireCommand() {
        // If a require command is contained here it is located at the first position
        if (null == commands) {
            return null;
        }
        if (!commands.isEmpty()) {
            final Command command = commands.get(0);
            if (command instanceof RequireCommand) {
                final RequireCommand requirecmd = (RequireCommand) command;
                return requirecmd;
            }
        }
        return null;
    }

    /**
     * A convenience method to get the if command if one is contained
     * 
     * @return the if command or null if none is contained
     */
    public final IfCommand getIfCommand() {
        if (null == commands) {
            return null;
        }
        for (final Command command : commands) {
            if (command instanceof IfCommand) {
                final IfCommand ifcommand = (IfCommand) command;
                return ifcommand;
            }
        }
        return null;
    }

    /**
     * A convenience method to get the test command if one is contained
     * 
     * @return the test command or null if none is contained
     */
    public final TestCommand getTestCommand() {
        final IfCommand ifCommand = getIfCommand();
        if (null == ifCommand) {
            return null;
        }

        return ifCommand.getTestcommand();
    }

    /**
     * A convenience method for directly accessing the unique id
     * 
     * @return -1 if there is no unique id for this rule; a value > -1 otherwise
     */
    public int getUniqueId() {
        if (null == ruleComment) {
            return -1;
        }

        return ruleComment.getUniqueid();
    }

    public final void setRuleComments(final RuleComment ruleComment) {
        this.ruleComment = ruleComment;
    }

    public final void setCommands(final ArrayList<Command> commands) {
        this.commands = commands;
    }

    public final int getLinenumber() {
        return linenumber;
    }

    public final void setLinenumber(final int linenumber) {
        this.linenumber = linenumber;
    }

    /**
     * @return the position
     */
    public final int getPosition() {
        return position;
    }

    /**
     * @param position the position to set
     */
    public final void setPosition(final int position) {
        this.position = position;
    }

    public final boolean isCommented() {
        return commented;
    }

    public final void setCommented(final boolean commented) {
        this.commented = commented;
    }

    /**
     * @return the text
     */
    public final String getText() {
        return text;
    }

    /**
     * @param text the text to set
     */
    public final void setText(final String text) {
        this.text = text;
    }

    /**
     * @return the errormsg
     */
    public final String getErrormsg() {
        return errormsg;
    }

    /**
     * @param errormsg the errormsg to set
     */
    public final void setErrormsg(final String errormsg) {
        this.errormsg = errormsg;
    }

    /**
     * @return the endlinenumber
     */
    public final int getEndlinenumber() {
        return endlinenumber;
    }

    /**
     * @param endlinenumber the endlinenumber to set
     */
    public final void setEndlinenumber(final int endlinenumber) {
        this.endlinenumber = endlinenumber;
    }

    @Override
    public String toString() {
        return "Name: " + ((null != ruleComment && null != ruleComment.getRulename()) ? ruleComment.getRulename() : null) + ": " + commands;
    }

    @Override
    public int compareTo(final Rule o) {
        return Integer.compare(linenumber, o.linenumber);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((commands == null) ? 0 : commands.hashCode());
        result = prime * result + (commented ? 1231 : 1237);
        result = prime * result + endlinenumber;
        result = prime * result + ((errormsg == null) ? 0 : errormsg.hashCode());
        result = prime * result + linenumber;
        result = prime * result + position;
        result = prime * result + ((ruleComment == null) ? 0 : ruleComment.hashCode());
        result = prime * result + ((text == null) ? 0 : text.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        Rule other = (Rule) obj;
        if (commands == null) {
            if (other.commands != null) {
                return false;
            }
        } else if (!commands.equals(other.commands)) {
            return false;
        }
        if (commented != other.commented) {
            return false;
        }
        if (endlinenumber != other.endlinenumber) {
            return false;
        }
        if (errormsg == null) {
            if (other.errormsg != null) {
                return false;
            }
        } else if (!errormsg.equals(other.errormsg)) {
            return false;
        }
        if (linenumber != other.linenumber) {
            return false;
        }
        if (position != other.position) {
            return false;
        }
        if (ruleComment == null) {
            if (other.ruleComment != null) {
                return false;
            }
        } else if (!ruleComment.equals(other.ruleComment)) {
            return false;
        }
        if (text == null) {
            if (other.text != null) {
                return false;
            }
        } else if (!text.equals(other.text)) {
            return false;
        }
        return true;
    }
}
