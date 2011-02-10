package com.zutubi.pulse.core.scm.svncl;

import static com.zutubi.pulse.core.scm.svncl.SvnConstants.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.zutubi.pulse.core.engine.api.ExecutionContext;
import com.zutubi.pulse.core.engine.api.ResourceProperty;
import com.zutubi.pulse.core.scm.api.Changelist;
import com.zutubi.pulse.core.scm.api.EOLStyle;
import com.zutubi.pulse.core.scm.api.ExcludePathPredicate;
import com.zutubi.pulse.core.scm.api.FileChange;
import com.zutubi.pulse.core.scm.api.Revision;
import com.zutubi.pulse.core.scm.api.ScmCapability;
import com.zutubi.pulse.core.scm.api.ScmClient;
import com.zutubi.pulse.core.scm.api.ScmContext;
import com.zutubi.pulse.core.scm.api.ScmException;
import com.zutubi.pulse.core.scm.api.ScmFeedbackHandler;
import com.zutubi.pulse.core.scm.api.ScmFile;
import com.zutubi.util.CollectionUtils;
import com.zutubi.util.Mapping;
import com.zutubi.util.Predicate;
import com.zutubi.util.StringUtils;
import com.zutubi.util.io.IOUtils;

/**
 * An {@link com.zutubi.pulse.core.scm.api.ScmClient} implementation that
 * wraps the command-line svn client.
 */
public class SvnClient implements ScmClient
{
    private final SvnConfiguration config;

    public SvnClient(SvnConfiguration config)
    {
        this.config = config;
    }

    @Override
    public void init(ScmContext context, ScmFeedbackHandler handler) throws ScmException
    {
        // no-op
    }
    
    @Override
    public void destroy(ScmContext context, ScmFeedbackHandler handler) throws ScmException
    {
        // no-op
    }

    @Override
    public void close()
    {
        // no-op
    }

    @Override
    public Set<ScmCapability> getCapabilities(ScmContext context)
    {
        // Support the core functionality, but not browsing, tagging or personal builds (yet).
        return EnumSet.of(ScmCapability.CHANGESETS, ScmCapability.POLL, ScmCapability.REVISIONS);
    }

    @Override
    public String getUid() throws ScmException
    {
        return getInfo(PATTERN_UUID, "Repository UUID");
    }

    private String getInfo(Pattern pattern, String description) throws ScmException
    {
        SvnCommandLine cl = new SvnCommandLine(config);
        List<String> stdout = cl.run(null, COMMAND_INFO, config.getUrl());
        for (String line: stdout)
        {
            Matcher matcher = pattern.matcher(line);
            if (matcher.matches())
            {
                return matcher.group(1);
            }
        }
        
        throw new ScmException(description + " not found in info output (" + StringUtils.join("\n", stdout) + ")");
    }

    @Override
    public String getLocation() throws ScmException
    {
        return config.getUsername() + "@" + config.getUrl();
    }
    
    @Override
    public List<ResourceProperty> getProperties(ExecutionContext context) throws ScmException
    {
        return Arrays.asList(new ResourceProperty("svn.url", config.getUrl()));
    }

    @Override
    public Revision checkout(ExecutionContext context, Revision revision, ScmFeedbackHandler handler) throws ScmException
    {
        if (revision == null)
        {
            revision = getLatestRevision(null);
        }

        SvnCommandLine cl = new SvnCommandLine(config);
        cl.run(handler, COMMAND_CHECKOUT, FLAG_REVISION, revision.getRevisionString(), FLAG_FORCE, config.getUrl(), context.getWorkingDir().getAbsolutePath());
        return revision;
    }
    
    @Override
    public Revision update(ExecutionContext context, Revision revision, ScmFeedbackHandler handler) throws ScmException
    {
        if (revision == null)
        {
            revision = getLatestRevision(null);
        }

        SvnCommandLine cl = new SvnCommandLine(config);
        cl.run(handler, COMMAND_UPDATE, FLAG_REVISION, revision.getRevisionString(), FLAG_FORCE, context.getWorkingDir().getAbsolutePath());
        return revision;
    }

    @Override
    public InputStream retrieve(ScmContext context, String path, Revision revision) throws ScmException
    {
        List<String> args = new LinkedList<String>();
        args.add(COMMAND_CAT);
        if (revision != null)
        {
            args.add(FLAG_REVISION);
            args.add(revision.getRevisionString());
        }
        args.add(join(config.getUrl(), path));
        
        // This implementation is somewhat naive in its memory usage.
        SvnCommandLine cl = new SvnCommandLine(config);
        List<String> lines = cl.run(null, args.toArray(new String[args.size()]));
        String output = StringUtils.join("\n", lines);
        return new ByteArrayInputStream(output.getBytes());
    }
    
    private String join(String... urlElements)
    {
        return StringUtils.join("/", true, urlElements);
    }

    @Override
    public void storeConnectionDetails(ExecutionContext context, File outputDir) throws ScmException, IOException
    {
        Properties props = new Properties();
        props.put("location", getLocation());

        FileOutputStream os = null;
        try
        {
            os = new FileOutputStream(new File(outputDir, "svn.properties"));
            props.store(os, "Subversion connection properties");
        }
        finally
        {
            IOUtils.close(os);
        }        
    }

    @Override
	public EOLStyle getEOLPolicy(ExecutionContext arg0) throws ScmException
	{
		return EOLStyle.BINARY;
	}

    @Override
    public Revision getLatestRevision(ScmContext context) throws ScmException
    {
        return new Revision(getInfo(PATTERN_LAST_REVISION, "Last changed revision"));
    }
    
    @Override
    public List<Revision> getRevisions(ScmContext context, Revision from, Revision to) throws ScmException
    {
        List<Changelist> changes = getChanges(null, from, to);
        Collections.sort(changes);

        List<Revision> result = new LinkedList<Revision>();
        for (Changelist change : changes)
        {
            result.add(change.getRevision());
        }

        return result;
    }
    
    @Override
    public List<Changelist> getChanges(ScmContext context, Revision fromRevision, Revision toRevision) throws ScmException
    {
        if (toRevision == null)
        {
            toRevision = getLatestRevision(null);
        }

        long from = Long.parseLong(fromRevision.getRevisionString()) + 1;
        long to = Long.parseLong(toRevision.getRevisionString());
        List<Changelist> changelists;
        
        if (from <= to)
        {
            SvnCommandLine commandLine = new SvnCommandLine(config);
            List<String> lines = commandLine.run(null, COMMAND_LOG, FLAG_REVISION, Long.toString(from) + ":" + Long.toString(to), FLAG_VERBOSE, FLAG_XML, config.getUrl());
            changelists = LogParser.parse(StringUtils.join("", lines));
            final Predicate<String> filter = new ExcludePathPredicate(config.getFilterPaths());
            
            // Remove all FileChange objects that are for filtered paths.
            changelists = CollectionUtils.map(changelists, new Mapping<Changelist, Changelist>()
            {
                @Override
                public Changelist map(Changelist c)
                {
                    return new Changelist(c.getRevision(), c.getTime(), c.getAuthor(), c.getComment(), CollectionUtils.filter(c.getChanges(), new Predicate<FileChange>()
                    {
                        @Override
                        public boolean satisfied(FileChange fc)
                        {
                            return filter.satisfied(fc.getPath());
                        }
                        
                    }));
                }
            });
            
            // Remove all changelists that no longer have any file changes.
            changelists = CollectionUtils.filter(changelists, new Predicate<Changelist>()
            {
                @Override
                public boolean satisfied(Changelist c)
                {
                    return c.getChanges().size() > 0;
                }
            });
        }
        else
        {
            changelists = Collections.emptyList();
        }
        
        return changelists;
    }

    @Override
    public Revision getPreviousRevision(ScmContext context, Revision revision, boolean isFile) throws ScmException
    {
        try
        {
            return revision.calculatePreviousNumericalRevision();
        }
        catch (NumberFormatException e)
        {
            throw new ScmException("Invalid revision '" + revision.getRevisionString() + "': " + e.getMessage());
        }
    }

    @Override
    public Revision parseRevision(ScmContext context, String revision) throws ScmException
    {
        try
        {
            long revisionNumber = Long.parseLong(revision);
            long latest = Long.parseLong(getLatestRevision(null).getRevisionString());
            if(revisionNumber > latest)
            {
                throw new ScmException("Revision '" + revision + "' does not exist in this repository");
            }

            return new Revision(revisionNumber);
        }
        catch(NumberFormatException e)
        {
            throw new ScmException("Invalid revision '" + revision + "': must be a valid revision number");
        }
    }

    @Override
    public List<ScmFile> browse(ScmContext context, String path, Revision revision) throws ScmException
    {
        throw new ScmException("Not yet implemented");
    }

    @Override
    public void tag(ScmContext scmContext, ExecutionContext executionContext, Revision revision, String comment, boolean moveExisting) throws ScmException
    {
        throw new ScmException("Not yet implemented");
    }

	@Override
	public String getEmailAddress(ScmContext arg0, String arg1) throws ScmException
	{
		return null;
	}
}
