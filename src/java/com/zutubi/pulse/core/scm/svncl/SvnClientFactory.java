package com.zutubi.pulse.core.scm.svncl;

import com.zutubi.pulse.core.scm.api.ScmClient;
import com.zutubi.pulse.core.scm.api.ScmClientFactory;
import com.zutubi.pulse.core.scm.api.ScmException;

/**
 * A factory for creating svn clients from svn config.
 */
public class SvnClientFactory implements ScmClientFactory<SvnConfiguration>
{
    @Override
    public ScmClient createClient(SvnConfiguration config) throws ScmException
    {
        return new SvnClient(config);
    }
}
