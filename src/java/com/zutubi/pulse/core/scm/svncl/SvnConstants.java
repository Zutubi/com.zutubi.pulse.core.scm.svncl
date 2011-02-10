package com.zutubi.pulse.core.scm.svncl;

import java.util.regex.Pattern;

/**
 * Constants shared by the Svn* classes.
 */
public class SvnConstants
{
    public static final String COMMAND_SVN = "svn";
    
    public static final String COMMAND_CAT = "cat";
    public static final String COMMAND_CHECKOUT = "checkout";
    public static final String COMMAND_INFO = "info";
    public static final String COMMAND_LOG = "log";
    public static final String COMMAND_UPDATE = "update";

    public static final String FLAG_FORCE = "--force";
    public static final String FLAG_NON_INTERACTIVE = "--non-interactive";
    public static final String FLAG_PASSWORD = "--password";
    public static final String FLAG_REVISION = "--revision";
    public static final String FLAG_USER = "--username";
    public static final String FLAG_VERBOSE = "--verbose";
    public static final String FLAG_XML = "--xml";
    
    public static final Pattern PATTERN_LAST_REVISION = Pattern.compile("Last Changed Rev:\\s+([0-9]+)");
    public static final Pattern PATTERN_UUID = Pattern.compile("Repository UUID:\\s+(.+)");    
}
