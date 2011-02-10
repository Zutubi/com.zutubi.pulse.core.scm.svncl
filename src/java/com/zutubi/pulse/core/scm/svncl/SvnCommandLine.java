package com.zutubi.pulse.core.scm.svncl;

import static com.zutubi.pulse.core.scm.svncl.SvnConstants.COMMAND_SVN;
import static com.zutubi.pulse.core.scm.svncl.SvnConstants.FLAG_NON_INTERACTIVE;
import static com.zutubi.pulse.core.scm.svncl.SvnConstants.FLAG_PASSWORD;
import static com.zutubi.pulse.core.scm.svncl.SvnConstants.FLAG_USER;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.zutubi.pulse.core.scm.api.ScmException;
import com.zutubi.pulse.core.scm.api.ScmFeedbackHandler;
import com.zutubi.pulse.core.util.process.AsyncProcess;
import com.zutubi.pulse.core.util.process.LineHandler;
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
        ProcessBuilder builder = new ProcessBuilder(resolveCommand(arguments));        
        List<String> output = new LinkedList<String>();
        
        AsyncProcess process = startProcess(handler, builder, output);
        int exitCode = waitForProcessToComplete(process, handler);
        if (exitCode != 0)
        {
            throw new ScmException("Child svn process exited with code " + exitCode);
        }
        
        return output;
    }

    private List<String> resolveCommand(String... command)
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
        return result;
    }

    private AsyncProcess startProcess(final ScmFeedbackHandler handler, ProcessBuilder builder, final List<String> output) throws ScmException
    {
        if (handler != null)
        {
            handler.status(getCleanedCommandLine(builder));
        }
        
        Process p;
        try
        {
            p = builder.start();
        }
        catch (IOException e)
        {
            throw new ScmException("Unable to start svn process: " + e.getMessage(), e);
        }
        
        AsyncProcess process = new AsyncProcess(p, new LineHandler()
        {
            @Override
            public void handle(String line, boolean error)
            {
                if (handler != null)
                {
                    handler.status(line);
                }
                
                if (!error)
                {
                    output.add(line);
                }
            }
            
        }, true);
        
        return process;
    }

    private String getCleanedCommandLine(ProcessBuilder builder)
    {
        StringBuilder result = new StringBuilder();
        result.append(">>");
        boolean suppress = false;
        for (String s: builder.command())
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

    private Integer waitForProcessToComplete(AsyncProcess process, final ScmFeedbackHandler handler) throws ScmException
    {
        Integer exitCode;
        try
        {
            do
            {
                exitCode = process.waitFor(10, TimeUnit.SECONDS);
                if (handler != null)
                {
                    handler.checkCancelled();
                }
            }
            while (exitCode == null);
        }
        catch (IOException e)
        {
            throw new ScmException("Error running svn process: " + e.getMessage(), e);
        }
        catch (InterruptedException e)
        {
            throw new ScmException("Interrupted while running svn process: " + e.getMessage(), e);
        }
        finally
        {
            process.destroy();
        }
        return exitCode;
    }
}
