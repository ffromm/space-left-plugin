package hudson.plugins.space_left;

import hudson.FilePath;
import hudson.model.FreeStyleProject;
import hudson.model.labels.LabelAtom;
import hudson.slaves.DumbSlave;
import hudson.slaves.SlaveComputer;
import hudson.slaves.WorkspaceList;
import junit.framework.TestCase;
import org.junit.Test;
import org.jvnet.hudson.test.HudsonTestCase;

import java.io.File;

/**
 * Tests the class to get the required space for a node
 */
public class RequiredSpaceTest extends HudsonTestCase {

    public void testGetRequiredSpace() throws Exception {
        // set COMBINATOR to "_" for better readability
        System.setProperty(WorkspaceList.class.getName(), "_");

        // init slave
        LabelAtom label = new LabelAtom("label");
        DumbSlave slave = this.createSlave(label);
        SlaveComputer c = slave.getComputer();
        c.connect(false).get(); // wait until it's connected
        if(c.isOffline()) {
            fail("Slave failed to go online: "+c.getLog());
        }

        RequiredSpace requiredSpace = new RequiredSpace(slave);

        assertEquals(0L, requiredSpace.getRequiredSpace());

        // run jobs on slave consuming space and storing it in build.xml
        // add project to slave
        FreeStyleProject project = this.createFreeStyleProject("spaceConsumer");
        project.setAssignedLabel(label);
        SpaceLeftProperty spaceLeftProperty = new SpaceLeftProperty();
        spaceLeftProperty.setRequiredSpace(2000000L);
        project.addProperty(spaceLeftProperty);

        FilePath workspace = c.getNode().getWorkspaceFor(project);
        assertNotNull(workspace);
        File testFile = new File("src/test/resources/testfile.txt");
        assertTrue(testFile.exists() && testFile.canRead());
        FilePath testFilePath = new FilePath(testFile);
        testFilePath.copyTo(workspace.child("testfile.txt"));

        project.scheduleBuild2(0).get();

        assertEquals(2000000L, requiredSpace.getRequiredSpace());

        String remote = workspace.getParent().getRemote();
        File remoteDir = new File(remote);

        String[] jobNamesBefore = remoteDir.list();
        assertEquals(1, jobNamesBefore.length);
        assertTrue(remoteDir.exists() && remoteDir.isDirectory());
        String jobName = workspace.getBaseName();
        FilePath workspace2 = workspace.getParent().child(jobName + "_2");
        int numCopied2 = workspace.copyRecursiveTo(workspace2);
        assertTrue(numCopied2 > 0);
        FilePath workspace3 = workspace.getParent().child(jobName + "_3");
        int numCopied3 = workspace.copyRecursiveTo(workspace3);
        assertTrue(numCopied3 > 0);
        String[] jobNamesAfter = remoteDir.list();
        assertEquals(3, jobNamesAfter.length);

        assertEquals(6000000L, requiredSpace.getRequiredSpace());
    }

    /**
     * Test the required space setting
     * @throws Exception
     */
    public void testGetRequiredProjectSpace() throws Exception {
        FreeStyleProject project = this.createFreeStyleProject();

        RequiredSpace requiredSpace = new RequiredSpace(null);

        assertEquals(0L, requiredSpace.getRequiredProjectSpace(project));

        SpaceLeftProperty spaceLeftProperty = new SpaceLeftProperty();
        spaceLeftProperty.setRequiredSpace(2000000L);
        project.addProperty(spaceLeftProperty);

        assertEquals(2000000L, requiredSpace.getRequiredProjectSpace(project));
    }

}
