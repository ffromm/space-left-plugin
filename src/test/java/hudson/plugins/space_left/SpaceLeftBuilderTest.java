package hudson.plugins.space_left;

import hudson.FilePath;
import hudson.model.*;
import hudson.model.labels.LabelAtom;
import hudson.slaves.DumbSlave;
import hudson.slaves.SlaveComputer;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;
import org.jvnet.hudson.test.HudsonTestCase;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: jet
 * Date: 4/23/12
 * Time: 1:32 PM
 * To change this template use File | Settings | File Templates.
 */
public class SpaceLeftBuilderTest extends HudsonTestCase {

    public void testPerform() throws Exception {
        LabelAtom label = new LabelAtom("label");
        DumbSlave slave = this.createSlave(label);
        SlaveComputer c = slave.getComputer();
        c.connect(false).get(); // wait until it's connected
        if(c.isOffline()) {
            fail("Slave failed to go online: "+c.getLog());
        }

        // add project to slave
        FreeStyleProject project = this.createFreeStyleProject();
        project.setAssignedLabel(label);

        SpaceLeftBuilder spaceLeftBuilder = new SpaceLeftBuilder();

        project.getBuildersList().add(spaceLeftBuilder);

        FreeStyleBuild build = project.scheduleBuild2(0).get();

        List<Action> actions = build.getActions();

        assertNotNull(actions);

        // copy file into workspace and check again

        long expectedSize = 1263616L + 4096L;   // testfile + dir

        File testFile = new File("src/test/resources/testfile.txt");

        assertTrue(testFile.exists() && testFile.canRead());

        FilePath testFilePath = new FilePath(testFile);

        testFilePath.copyTo(build.getWorkspace().child("testfile.txt"));

        build = project.scheduleBuild2(0).get();

        actions = build.getActions();
        assertEquals(2, actions.size());

        Action action = actions.get(1);
        assertTrue(action instanceof ParametersAction);

        ParametersAction parametersAction = (ParametersAction) action;
        StringParameterValue workspaceSize = (StringParameterValue) parametersAction.getParameter("workspaceSize");

        assertNotNull(workspaceSize);

        long size = Long.parseLong(workspaceSize.value);
        assertEquals(expectedSize, size);

        File buildDirFor = Jenkins.getInstance().getBuildDirFor(build.getParent());

        assertNotNull(buildDirFor);
        assertTrue(buildDirFor.exists() && buildDirFor.canRead());

        File buildFile = new File(buildDirFor.getAbsolutePath() + "/2/build.xml");

        assertTrue(buildFile.exists() && buildFile.canRead());

        List<String> lines = FileUtils.readLines(buildFile);

        String workspaceSizeLine = "";
        boolean gotParam = false;

        for (String line : lines) {
            if(gotParam) {
                workspaceSizeLine = line;
                break;
            }

            if(line.contains("workspaceSize")) {
                gotParam = true;
            }
        }

        assertTrue(gotParam);

        assertEquals("          <value>" + expectedSize + "</value>", workspaceSizeLine);
    }
}
