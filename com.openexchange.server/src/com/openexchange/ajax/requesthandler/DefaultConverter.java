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

package com.openexchange.ajax.requesthandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.openexchange.exception.OXException;
import com.openexchange.tools.servlet.AjaxExceptionCodes;
import com.openexchange.tools.session.ServerSession;

/**
 * {@link DefaultConverter} - The default {@link Converter} implementation.
 *
 * @author <a href="mailto:francisco.laguna@open-xchange.com">Francisco Laguna</a>
 */
public class DefaultConverter implements Converter {

    private final Map<String, List<Node>> understandsFormat;

    private final Map<String, List<Node>> suppliesFormat;

    private final Map<Conversion, Step> cachedSteps;

    /**
     * Initializes a new {@link DefaultConverter}.
     */
    public DefaultConverter() {
        super();
        understandsFormat = new ConcurrentHashMap<String, List<Node>>();
        suppliesFormat = new ConcurrentHashMap<String, List<Node>>();
        cachedSteps = new ConcurrentHashMap<Conversion, Step>();
    }

    /**
     * Adds specified result converter.
     *
     * @param converter The converter
     */
    public void addConverter(final ResultConverter converter) {
        final Node n = new Node();
        n.converter = converter;
        {
            // First let's connect this node as a follow-up to all nodes outputting what we accept as input
            final Edge edge = new Edge();
            edge.node = n;

            final List<Node> nodesWhoseOutputIsUnderstood = suppliesFormat.get(converter.getInputFormat());
            if (nodesWhoseOutputIsUnderstood != null && !nodesWhoseOutputIsUnderstood.isEmpty()) {
                for (final Node node : nodesWhoseOutputIsUnderstood) {
                    node.edges.add(edge);
                }
            }
        }
        {
            // Next, we'll find our current followers
            final String outputFormat = converter.getOutputFormat();
            final List<Node> nodesWhoUnderstandMe = understandsFormat.get(outputFormat);
            if (nodesWhoUnderstandMe != null && !nodesWhoUnderstandMe.isEmpty()) {
                for (final Node node : nodesWhoUnderstandMe) {
                    final Edge edge = new Edge();
                    edge.node = node;
                    n.edges.add(edge);
                }
            }
        }

        {
            // Next update the lists
            List<Node> understanders = understandsFormat.get(converter.getInputFormat());
            if (understanders == null) {
                understanders = new LinkedList<Node>();
                understandsFormat.put(converter.getInputFormat(), understanders);
            }
            understanders.add(n);

            List<Node> suppliers = suppliesFormat.get(converter.getOutputFormat());
            if (suppliers == null) {
                suppliers = new LinkedList<Node>();
                suppliesFormat.put(converter.getOutputFormat(), suppliers);
            }
            suppliers.add(n);
        }

    }

    /**
     * Removes given result converter.
     *
     * @param resultConverter The result converter
     */
    public void removeConverter(final ResultConverter resultConverter) {
        // Nothing to do?
    }

    @Override
    public void convert(final String fromFormat, final String toFormat, final AJAXRequestData requestData, final AJAXRequestResult result, final ServerSession session) throws OXException {
        if (fromFormat.equals(toFormat)) {
            return;
        }
        if (result == AJAXRequestResult.EMPTY_REQUEST_RESULT) {
        	return;
        }
    	try {
            Step path = getShortestPath(fromFormat, toFormat);
            while (path != null) {
                path.converter.convert(requestData, result, session, this);
                result.setFormat(path.converter.getOutputFormat());
                path = path.next;
            }
        } catch (NoSuchPath e) {
            throw AjaxExceptionCodes.NO_SUCH_CONVERSION_PATH.create(e, fromFormat, toFormat, requestData.getModule(), requestData.getAction());
        }
    }

    /**
     * Gets the shortest path from <code>from</code> to <code>to</code>.
     *
     * @param from The from format
     * @param to The target format
     * @return The calculated path
     */
    public Step getShortestPath(final String from, final String to) throws NoSuchPath {
        final Conversion conversion = new Conversion(from, to);
        final Step step = cachedSteps.get(conversion);
        if (step != null) {
            return step;
        }
        final Map<Node, Mark> markings = new HashMap<Node, Mark>();
        List<Edge> edges = getInitialEdges(from);
        Mark currentMark = new Mark();
        currentMark.weight = 0;
        Node currentNode = new Node();
        currentNode.edges = edges;

        while (true) {
            Mark nextMark = null;
            Node nextNode = null;
            for (final Edge edge : edges) {
                if (edge.node.converter.getOutputFormat().equals(to)) {
                    // I guess we're done;
                    final Mark m = new Mark();
                    m.previous = currentNode;
                    final Step newStep = unwind(m, edge, markings);
                    cachedSteps.put(conversion, newStep);
                    return newStep;
                }
                Mark mark = markings.get(edge.node);
                if (mark == null) {
                    mark = new Mark();
                    markings.put(edge.node, mark);
                }

                if (!mark.visited && mark.weight > currentMark.weight + edge.weight()) {
                    mark.weight = currentMark.weight + edge.weight();
                    mark.previous = currentNode;
                    if (nextMark == null || nextMark.weight > mark.weight) {
                        nextMark = mark;
                        nextNode = edge.node;
                    }
                }
            }

            currentMark.visited = true;

            if (nextMark == null) {
                // This was a dead end
                currentMark.weight = 100;
                while (nextMark == null) {
                    if (currentMark == null || currentMark.previous == null) {
                        throw new NoSuchPath(from , to);
                    }
                    currentNode = currentMark.previous;
                    currentMark = markings.get(currentNode);
                    for (final Edge edge : currentNode.edges) {
                        final Mark mark = markings.get(edge.node);
                        if (!mark.visited && (nextMark == null || nextMark.weight > mark.weight)) {
                            nextMark = mark;
                            nextNode = edge.node;
                        }
                    }
                }
            }

            currentMark = nextMark;
            currentNode = nextNode;
            edges = nextNode.edges;

        }

    }

    private Step unwind(Mark currentMark, final Edge edge, final Map<Node, Mark> markings) {
        Step current = new Step();
        current.converter = edge.node.converter;

        while (currentMark != null && currentMark.previous != null && currentMark.previous.converter != null) {
            final Step step = new Step();
            step.converter = currentMark.previous.converter;
            step.next = current;
            current = step;
            currentMark = markings.get(currentMark.previous);
        }

        return current;
    }

    // Synthetic edges for entry
    private List<Edge> getInitialEdges(final String format) throws NoSuchPath {
        final List<Node> list = understandsFormat.get(format);

        if (list == null || list.isEmpty()) {
            throw new NoSuchPath(format);
        }
        final List<Edge> edges = new ArrayList<Edge>(list.size());
        for (final Node node : list) {
            final Edge edge = new Edge();
            edge.node = node;
            edges.add(edge);
        }

        return edges;
    }

    public static final class Step {

        public Step next;

        public ResultConverter converter;
    }

    public static final class Mark {

        public Node previous;

        public int weight = Integer.MAX_VALUE;

        public boolean visited = false;
    }

    public static final class Edge {

        public Node node;

        public int weight() {
            switch (node.converter.getQuality()) {
            case GOOD:
                return 1;
            case BAD:
                return 2;
            }
            return 2;
        }
    }

    public static final class Node {

        public ResultConverter converter;

        public List<Edge> edges = new LinkedList<Edge>();
    }

    public static final class Conversion {

        private final String from;

        private final String to;

        private final int hashCode;

        Conversion(final String from, final String to) {
            super();
            this.from = from;
            this.to = to;

            final int prime = 31;
            int result = 1;
            result = prime * result + ((from == null) ? 0 : from.hashCode());
            result = prime * result + ((to == null) ? 0 : to.hashCode());
            hashCode = result;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Conversion)) {
                return false;
            }
            final Conversion other = (Conversion) obj;
            if (from == null) {
                if (other.from != null) {
                    return false;
                }
            } else if (!from.equals(other.from)) {
                return false;
            }
            if (to == null) {
                if (other.to != null) {
                    return false;
                }
            } else if (!to.equals(other.to)) {
                return false;
            }
            return true;
        }

    }

    // ----------------------------------------------------------------------------- //

    static final class NoSuchPath extends Throwable {

        /**
         * serialVersionUID
         */
        private static final long serialVersionUID = 1L;

        NoSuchPath(String format) {
            super("Can't convert from " + format);
        }

        NoSuchPath(String from, String to) {
            super("Can't find path from " + from + " to " + to);
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }

}
