package hudson.plugins.space_left;


import hudson.*;
import hudson.model.*;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.tasks.Builder;
import hudson.tasks.Shell;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: jet
 * Date: 3/21/12
 * Time: 12:31 PM
 * To change this template use File | Settings | File Templates.
 */
public class SpaceLeftBuilder extends Builder {


    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        listener.getLogger().println("getting workspace size...");

        FilePath workspace = build.getWorkspace();
        Node node = build.getBuiltOn();

        Shell s = new Shell("#!/bin/bash\ndu -sb");
        FilePath scriptFile = s.createScriptFile(workspace);

        OutputStream out = new ByteArrayOutputStream();

        int r = launcher.launch().cmds(s.buildCommandLine(scriptFile)).envs(getEnvironment(node)).stdout(out).pwd(workspace).join();

        String size = out.toString();
        size = size.replaceAll("\\D", "");

        listener.getLogger().println("workspace size: " + size);
        listener.getLogger().println("launch result was: " + r);

        if (r != 0) {
            throw new AbortException("Setup script failed");
        }

        StringParameterValue value = new StringParameterValue("workspaceSize", size);
        List<ParameterValue> params = new ArrayList<ParameterValue>();
        params.add(value);

        build.addAction(new ParametersAction(params));
        build.save();
        listener.getLogger().println("...done.");
        return true;
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<Builder> {
        public String getDisplayName() {
            return "Persist workspaceSize for a build";
        }

        public SpaceLeftBuilder newInstance(StaplerRequest req, JSONObject data) {
            return new SpaceLeftBuilder();
        }
    }

    /**
     * Returns the environment variables for the given node.
     * @param node node to get the environment variables from
     * @return the environment variables for the given node
     */
    private EnvVars getEnvironment(Node node) {
        EnvironmentVariablesNodeProperty env = node.getNodeProperties().get(EnvironmentVariablesNodeProperty.class);
        return env != null ? env.getEnvVars() : new EnvVars();
    }
}
