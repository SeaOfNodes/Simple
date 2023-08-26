/*
 * DO NOT REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Contributor(s):
 *
 * The Initial Developer of the Original Software is
 * The Ecstasy Project (https://github.com/xtclang/).
 *
 * This file is part of the Sea Of Nodes Simple Programming language
 * implementation. See https://github.com/SeaOfNodes/Simple
 *
 * The contents of this file are subject to the terms of the
 * Apache License Version 2 (the "APL"). You may not use this
 * file except in compliance with the License. A copy of the
 * APL may be obtained from:
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package com.seaofnodes.simple.lexer.ecstasy;

import java.text.MessageFormat;

import java.util.Arrays;

import static com.seaofnodes.util.Handy.quotedString;


/**
 * A listener for errors being reported about source code, compilation, assembly, or verification of
 * XVM structures.
 */
public class ErrorListener
    {
    // ----- API -----------------------------------------------------------------------------------

    /**
     * Handles the logging of an error that originates in Ecstasy source code.
     *
     * @param severity    the severity level of the error; one of
     *                    {@link Severity#INFO}, {@link Severity#WARNING},
     *                    {@link Severity#ERROR}, or {@link Severity#FATAL}
     * @param sCode       the error code that identifies the error message
     * @param aoParam     the parameters for the error message; may be null
     * @param source      the source code (optional)
     * @param lPosStart   the position in the source where the error was detected
     * @param lPosEnd     the position in the source at which the error concluded
     *
     * @return true to attempt to abort the process that reported the error, or
     *         false to attempt to continue the process
     */
    public void log(Severity severity, String sCode, Object[] aoParam,
                        Source source, long lPosStart, long lPosEnd)
        {
            System.err.println(new ErrorInfo(severity, sCode, aoParam, source, lPosStart, lPosEnd));
        }


    // ----- inner class: ErrorInfo ----------------------------------------------------------------

    /**
     * Represents the information logged for a single error.
     */
    class ErrorInfo
        {
        /**
         * Construct an ErrorInfo object.
         *
         * @param severity    the severity level of the error; one of
         *                    {@link Severity#INFO}, {@link Severity#WARNING,
         *                    {@link Severity#ERROR}, or {@link Severity#FATAL}
         * @param sCode       the error code that identifies the error message
         * @param aoParam     the parameters for the error message; may be null
         * @param source      the source code
         * @param lPosStart   the starting position in the source code
         * @param lPosEnd     the ending position in the source code
         */
        public ErrorInfo(Severity severity, String sCode, Object[] aoParam,
                Source source, long lPosStart, long lPosEnd)
            {
            m_severity   = severity;
            m_sCode      = sCode;
            m_aoParam    = aoParam;
            m_source     = source;
            m_lPosStart  = lPosStart;
            m_lPosEnd    = lPosEnd;
            }

        /**
         * Construct an ErrorInfo object.
         *
         * @param severity    the severity level of the error; one of
         *                    {@link Severity#INFO}, {@link Severity#WARNING,
         *                    {@link Severity#ERROR}, or {@link Severity#FATAL}
         * @param sCode       the error code that identifies the error message
         * @param aoParam     the parameters for the error message; may be null
         */
        public ErrorInfo(Severity severity, String sCode, Object[] aoParam)
            {
            m_severity = severity;
            m_sCode    = sCode;
            m_aoParam  = aoParam;
            // TODO need to be able to ask the XVM structure for the source & location
            }

        /**
         * @return the Severity of the error
         */
        public Severity getSeverity()
            {
            return m_severity;
            }

        /**
         * @return the error code
         */
        public String getCode()
            {
            return m_sCode;
            }

        /**
         * @return the error message parameters
         */
        public Object[] getParams()
            {
            return m_aoParam;
            }

        /**
         * Produce a localized message based on the error code and related parameters.
         *
         * @return a formatted message for display that includes the error code
         */
        public String getMessage()
            {
            return getMessageText();
            }

        /**
         * Produce a localized message based on the error code and related parameters.
         *
         * @return a formatted message for display that doesn't include the error code
         */
        public String getMessageText()
            {
            return MessageFormat.format(getCode(), getParams());
            }

        /**
         * @return the source code
         */
        public Source getSource()
            {
            return m_source;
            }

        /**
         * @return the line number (zero based) at which the error occurred
         */
        public int getLine()
            {
            return Source.calculateLine(m_lPosStart);
            }

        /**
         * @return the offset (zero based) at which the error occurred
         */
        public int getOffset()
            {
            return Source.calculateOffset(m_lPosStart);
            }

        /**
         * @return the line number (zero based) at which the error concluded
         */
        public int getEndLine()
            {
            return Source.calculateLine(m_lPosEnd);
            }

        /**
         * @return the offset (zero based) at which the error concluded
         */
        public int getEndOffset()
            {
            return Source.calculateOffset(m_lPosEnd);
            }

        /**
         * @return an ID that allows redundant errors to be filtered out
         */
        public String genUID()
            {
            StringBuilder sb = new StringBuilder();
            sb.append(m_severity.ordinal())
                    .append(':')
                    .append(m_sCode);

            if (m_aoParam != null)
                {
                sb.append('#')
                  .append(Arrays.hashCode(m_aoParam));
                }

            if (!m_sCode.startsWith("VERIFY"))
                {
                if (m_source != null)
                    {
                    sb.append(':')
                      .append(m_source.getFileName())
                      .append(':')
                      .append(m_lPosStart)
                      .append(':')
                      .append(m_lPosStart);
                    }
                }

            return sb.toString();
            }

        @Override
        public String toString()
            {
            StringBuilder sb = new StringBuilder();

            // source code location
            if (m_source != null)
                {
                String sFile = m_source.getFileName();
                if (sFile != null)
                    {
                        sb.append(sFile)
                            .append(':').append(getLine() + 1)
                            .append(' ');
                    }

                sb.append("[")
                  .append(getLine() + 1)
                  .append(':')
                  .append(getOffset() + 1);

                if (getEndLine() != getLine() || getEndOffset() != getOffset())
                    {
                    sb.append("..")
                      .append(getEndLine() + 1)
                      .append(':')
                      .append(getEndOffset() + 1);
                    }

                sb.append("] ");
                }


            // localized message
            sb.append(getMessage());

            // source code snippet
            if (m_source != null && m_lPosStart != m_lPosEnd)
                {
                String sSource = m_source.toString(m_lPosStart, m_lPosEnd);
                if (sSource.length() > 80)
                    {
                    sSource = sSource.substring(0, 77) + "...";
                    }

                sb.append(" (")
                  .append(quotedString(sSource))
                  .append(')');
                }

            return sb.toString();
            }

        private final Severity     m_severity;
        private final String       m_sCode;
        private final Object[]     m_aoParam;
        private       Source       m_source;
        private       long         m_lPosStart;
        private       long         m_lPosEnd;
        }

    }