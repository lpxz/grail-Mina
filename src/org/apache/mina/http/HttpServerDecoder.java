/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 * 
 */
package org.apache.mina.http;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.mina.api.IoSession;
import org.apache.mina.filter.codec.ProtocolDecoder;
import org.apache.mina.filterchain.ReadFilterChainController;
import org.apache.mina.http.api.HttpEndOfContent;
import org.apache.mina.http.api.HttpMethod;
import org.apache.mina.http.api.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpServerDecoder implements ProtocolDecoder {
    private static final Logger LOG = LoggerFactory.getLogger(HttpServerCodec.class);

    /** Key for decoder current state */
    private static final String DECODER_STATE_ATT = "http.ds";

    /** Key for the partial HTTP requests head */
    private static final String PARTIAL_HEAD_ATT = "http.ph";

    /** Key for the number of bytes remaining to read for completing the body */
    private static final String BODY_REMAINING_BYTES = "http.brb";

    /** Regex to parse HttpRequest Request Line */
    public static final Pattern REQUEST_LINE_PATTERN = Pattern.compile(" ");

    /** Regex to parse out QueryString from HttpRequest */
    public static final Pattern QUERY_STRING_PATTERN = Pattern.compile("\\?");

    /** Regex to parse out parameters from query string */
    public static final Pattern PARAM_STRING_PATTERN = Pattern.compile("\\&|;");

    /** Regex to parse out key/value pairs */
    public static final Pattern KEY_VALUE_PATTERN = Pattern.compile("=");

    /** Regex to parse raw headers and body */
    public static final Pattern RAW_VALUE_PATTERN = Pattern.compile("\\r\\n\\r\\n");

    /** Regex to parse raw headers from body */
    public static final Pattern HEADERS_BODY_PATTERN = Pattern.compile("\\r\\n");

    /** Regex to parse header name and value */
    public static final Pattern HEADER_VALUE_PATTERN = Pattern.compile(": ");

    /** Regex to split cookie header following RFC6265 Section 5.4 */
    public static final Pattern COOKIE_SEPARATOR_PATTERN = Pattern.compile(";");

    @Override
    public Object decode(IoSession session, ByteBuffer msg, ReadFilterChainController controller) {
        DecoderState state = session.getAttribute(DECODER_STATE_ATT);

        switch (state) {
        case HEAD:
            LOG.debug("decoding HEAD");
            // grab the stored a partial HEAD request
            ByteBuffer oldBuffer = session.getAttribute(PARTIAL_HEAD_ATT);
            // concat the old buffer and the new incoming one
            msg = ByteBuffer.allocate(oldBuffer.remaining() + msg.remaining()).put(oldBuffer).put(msg);
            msg.flip();
            // now let's decode like it was a new message

        case NEW:
            LOG.debug("decoding NEW");
            HttpRequestImpl rq = parseHttpRequestHead(msg);

            if (rq == null) {
                // we copy the incoming BB because it's going to be recycled by the inner IoProcessor for next reads
                ByteBuffer partial = ByteBuffer.allocate(msg.remaining());
                partial.put(msg);
                partial.flip();
                // no request decoded, we accumulate
                session.setAttribute(PARTIAL_HEAD_ATT, partial);
                session.setAttribute(DECODER_STATE_ATT, DecoderState.HEAD);
            } else {
                controller.callReadNextFilter(session, rq);

                // is it a request with some body content ?
                if (rq.getMethod() == HttpMethod.POST || rq.getMethod() == HttpMethod.PUT) {
                    LOG.debug("request with content");
                    session.setAttribute(DECODER_STATE_ATT, DecoderState.BODY);

                    String contentLen = rq.getHeader("content-length");

                    if (contentLen != null) {
                        LOG.debug("found content len : {}", contentLen);
                        session.setAttribute(BODY_REMAINING_BYTES, new Integer(contentLen));
                    } else {
                        throw new RuntimeException("no content length !");
                    }
                } else {
                    LOG.debug("request without content");
                    session.setAttribute(DECODER_STATE_ATT, DecoderState.NEW);

                }
            }

            return null;

        case BODY:
            LOG.debug("decoding BODY");
            int chunkSize = msg.remaining();
            // send the chunk of body
            controller.callReadNextFilter(session, msg);
            // do we have reach end of body ?
            int remaining = (Integer) session.getAttribute(BODY_REMAINING_BYTES);
            remaining -= chunkSize;

            if (remaining <= 0) {
                LOG.debug("end of HTTP body");
                controller.callReadNextFilter(session, new HttpEndOfContent());
                session.setAttribute(DECODER_STATE_ATT, DecoderState.NEW);
                session.removeAttribute(BODY_REMAINING_BYTES);
            } else {
                session.setAttribute(BODY_REMAINING_BYTES, new Integer(remaining));
            }

            break;

        default:
            throw new RuntimeException("Unknonwn decoder state : " + state);
        }

        return null;
    }

    @Override
    public Object finishDecode(IoSession session) throws Exception {
        return null;
    }

    @Override
    public void dispose(IoSession session) throws Exception {
    }

    private HttpRequestImpl parseHttpRequestHead(ByteBuffer buffer) {
        String raw = new String(buffer.array(), 0, buffer.limit(), Charset.forName("ISO-8859-1"));
        String[] headersAndBody = RAW_VALUE_PATTERN.split(raw, -1);

        if (headersAndBody.length <= 1) {
            // we didn't receive the full HTTP head
            return null;
        }

        String[] headerFields = HEADERS_BODY_PATTERN.split(headersAndBody[0]);
        headerFields = ArrayUtil.dropFromEndWhile(headerFields, "");

        String requestLine = headerFields[0];
        Map<String, String> generalHeaders = new HashMap<String, String>();

        for (int i = 1; i < headerFields.length; i++) {
            String[] header = HEADER_VALUE_PATTERN.split(headerFields[i]);
            generalHeaders.put(header[0].toLowerCase(), header[1]);
        }

        String[] elements = REQUEST_LINE_PATTERN.split(requestLine);
        HttpMethod method = HttpMethod.valueOf(elements[0]);
        HttpVersion version = HttpVersion.fromString(elements[2]);
        String[] pathFrags = QUERY_STRING_PATTERN.split(elements[1]);
        String requestedPath = pathFrags[0];

        // we put the buffer position where we found the beginning of the HTTP body
        buffer.position(headersAndBody[0].length() + 4);

        return new HttpRequestImpl(version, method, requestedPath, generalHeaders);
    }
}
