package com.zutubi.pulse.core.scm.svncl;

import static com.zutubi.pulse.core.scm.svncl.SvnConstants.COMMAND_SVN;
import static com.zutubi.pulse.core.scm.svncl.SvnConstants.FLAG_NON_INTERACTIVE;
import static com.zutubi.pulse.core.scm.svncl.SvnConstants.FLAG_PASSWORD;
import static com.zutubi.pulse.core.scm.svncl.SvnConstants.FLAG_USER;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import com.zutubi.pulse.core.scm.api.ScmException;
import com.zutubi.pulse.core.scm.api.ScmFeedbackHandler;
import com.zutubi.pulse.core.scm.process.api.ScmLineHandlerSupport;
import com.zutubi.pulse.core.scm.process.api.ScmProcessRunner;
import com.zutubi.util.StringUtils;

/**
 * A wrapper around the 'svn' command line utility.  Handles running the
 * external process, and providing global options such as credentials.
 */
public class SvnCommandLine
{
	private SvnConfiguration config;

    public SvnCommandLine(SvnConfiguration config)
    {
        this.config = config;
    }

    /**
     * Runs an external svn command with the given arguments.  The arguments
     * need not include credentials as these will be added by default.  The
     * process is run in a separate thread, but this method will await its
     * completion (unless cancelled via the handler).
     * 
     * @param handler   if not null, a handler that will receive output from
     *                  the process as it runs, and will be polled regularly to
     *                  check for a cancelled operation
     * @param arguments arguments to pass to the svn command, e.g.
     *                  {@code {"info", "svn://myserver/myproject"}}
     * @return standard output from the command, as a list of individual lines
     * @throws ScmException on any error, including a non-zero exit code from
     *                      the child process
     */
    public List<String> run(final ScmFeedbackHandler handler, String... arguments) throws ScmException
    {
        final List<String> output = new LinkedList<String>();
    	ScmProcessRunner runner = new ScmProcessRunner("svn");
    	runner.setInactivityTimeout(config.getInactivityTimeout());
    	runner.runProcess(new ScmLineHandlerSupport()
    	{
			@Override
			public void handleStdout(String line)
			{
				output.add(line);
			}

			@Override
			public void handleCommandLine(String commandLine)
			{
				if (handler != null)
				{
					handler.status(">> " + getCleanedCommandLine(commandLine));
				}
			}
    	}, resolveCommand(arguments));
    	
        return output;
    }

    private String[] resolveCommand(String... command)
    {
        List<String> result = new LinkedList<String>();
        result.add(COMMAND_SVN);
        
        if (StringUtils.stringSet(config.getUsername()))
        {
            result.add(FLAG_USER);
            result.add(config.getUsername());
        }
        
        if (StringUtils.stringSet(config.getPassword()))
        {
            result.add(FLAG_PASSWORD);
            result.add(config.getPassword());
        }
        
        result.add(FLAG_NON_INTERACTIVE);
        result.addAll(Arrays.asList(command));
        return result.toArray(new String[result.size()]);
    }

    private String getCleanedCommandLine(String commandLine)
    {
        StringBuilder result = new StringBuilder();
        result.append(">>");
        boolean suppress = false;
        for (String s: commandLine.split("\\s+"))
        {
            result.append(" ");
            if (suppress)
            {
                result.append("<suppressed>");
            }
            else
            {
                result.append(s);
            }
            
            
            suppress = s.equals(FLAG_PASSWORD);
        }
        return result.toString();
    }
}
