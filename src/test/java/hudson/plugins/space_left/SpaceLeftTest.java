package hudson.plugins.space_left;

import hudson.FilePath;
import hudson.model.*;
import hudson.model.labels.LabelAtom;
import hudson.slaves.*;
import org.junit.Test;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.PretendSlave;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * Created with IntelliJ IDEA.
 * User: jet
 * Date: 4/5/12
 * Time: 2:14 PM
 * To change this template use File | Settings | File Templates.
 */
public class SpaceLeftTest extends HudsonTestCase {

    /**
     * Test free space calculation
     * @throws Exception
     */

    public void notestGetFreeSpace() throws Exception {

        // init slave
        PretendSlave pretendSlave = new PretendSlave("pretendSlave", null, 1, Node.Mode.NORMAL, "", null, null);

        LabelAtom label = new LabelAtom("label");
        DumbSlave slave = this.createSlave(label);
        SlaveComputer c = slave.getComputer();
        c.connect(false).get(); // wait until it's connected
        if(c.isOffline()) {
            fail("Slave failed to go online: "+c.getLog());
        }

        SpaceLeft spaceLeft = new SpaceLeft();

        Long freeSpace = spaceLeft.getFreeSpace(pretendSlave, null, -1L);

        assertEquals(Long.valueOf(0L), freeSpace);

        freeSpace = spaceLeft.getFreeSpace(slave, null, -1L);

        assertTrue(freeSpace > 0L);

        // add project to slave
        FreeStyleProject project = this.createFreeStyleProject();
        project.setAssignedLabel(label);

        Queue.BuildableItem item = new Queue.BuildableItem(new Queue.WaitingItem(Calendar.getInstance(), project, new ArrayList<Action>()));

        // set required space property on project, that needs lots of space
        SpaceLeftProperty spaceLeftProperty = new SpaceLeftProperty();
        spaceLeftProperty.setRequiredSpace(Long.MAX_VALUE / 4L);

        project.addProperty(spaceLeftProperty);

        // reset required space property to 1 MB
        spaceLeftProperty.setRequiredSpace(1000000L);

        Long otherFreeSpace = spaceLeft.getFreeSpace(slave, project, -1L);



        assertEquals(otherFreeSpace - getRemoteUsableSpace(project.getSomeWorkspace()), 1000000L);

        assertTrue((Math.abs(otherFreeSpace - (freeSpace - 1000000L)) < 100000L) );

        // take required space of other projects on slave into count
        FreeStyleProject otherProject = this.createFreeStyleProject();
        otherProject.setAssignedLabel(label);
        SpaceLeftProperty otherSpaceLeftProperty = new SpaceLeftProperty();
        otherSpaceLeftProperty.setRequiredSpace(2000000L);
        otherProject.addProperty(otherSpaceLeftProperty);
        FreeStyleBuild b = otherProject.scheduleBuild2(0).get();

        assertTrue(b.getWorkspace().exists());

        otherFreeSpace = spaceLeft.getFreeSpace(slave, project, -1L);

        assertTrue((Math.abs(otherFreeSpace - (freeSpace - 3000000L)) < 100000L) );

        StringParameterValue value = new StringParameterValue("REQUIRED_SPACE", "3000000");
        List<ParameterValue> params = new ArrayList<ParameterValue>();
        params.add(value);
        item.addAction(new ParametersAction(params));

        otherFreeSpace = spaceLeft.getFreeSpace(slave, project, 3000000L);

        assertTrue((Math.abs(otherFreeSpace - (freeSpace - 5000000L)) < 100000L) );
    }

    /**
     * Test the required space setting
     * @throws Exception
     */
    @Test
    public void testGetRequiredSpace() throws Exception {
        FreeStyleProject project = this.createFreeStyleProject();

        SpaceLeft spaceLeft = new SpaceLeft();

        assertEquals(Long.valueOf(0L), spaceLeft.getRequiredSpace(project));

        SpaceLeftProperty spaceLeftProperty = new SpaceLeftProperty();
        spaceLeftProperty.setRequiredSpace(2000000L);
        project.addProperty(spaceLeftProperty);

        assertEquals(Long.valueOf(2000000L), spaceLeft.getRequiredSpace(project));
    }

    /**
     * Test the storage of the workspace size in build.xml
     * @throws Exception
     */
    @Test
    public void testGetFreeSpaceWorkspaceSizeStorage() throws Exception {
        // init slave
        LabelAtom label = new LabelAtom("label");
        DumbSlave slave = this.createSlave(label);
        SlaveComputer c = slave.getComputer();
        c.connect(false).get(); // wait until it's connected
        if(c.isOffline()) {
            fail("Slave failed to go online: "+c.getLog());
        }

        // run jobs on slave consuming space and storing it in build.xml
        // add project to slave
        FreeStyleProject project = this.createFreeStyleProject("spaceConsumer");
        project.setAssignedLabel(label);

        SpaceLeftBuilder spaceLeftBuilder = new SpaceLeftBuilder();

        project.getBuildersList().add(spaceLeftBuilder);
        File testFile = new File("src/test/resources/testfile.txt");

        assertTrue(testFile.exists() && testFile.canRead());

        FilePath testFilePath = new FilePath(testFile);

        FreeStyleBuild build = project.scheduleBuild2(0, null, new ParametersAction(new StringParameterValue("componentName", "compName"))).get();

        testFilePath.copyTo(build.getWorkspace().child("testfile.txt"));

        project.scheduleBuild2(0, null, new ParametersAction(new StringParameterValue("componentName", "compName"))).get();

        SpaceLeft spaceLeft = new SpaceLeft();

        Map<String, String> buildSizesMap = spaceLeft.getBuildParamValueMap("spaceConsumer", "componentName", "workspaceSize");

        assertNotNull(buildSizesMap);

        assertEquals(1, buildSizesMap.size());

        Map.Entry<String, String> next = buildSizesMap.entrySet().iterator().next();
        assertEquals("compName", next.getKey());
        assertEquals("1267712", next.getValue());
    }

    /**
     * Test multiple workspaces for one Job.
     * @throws Exception
     */
    @Test
    public void testGetFreeSpaceWithConcurrentWorkspaces() throws Exception {

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

        SpaceLeft spaceLeft = new SpaceLeft();
        Long freeSpace = spaceLeft.getFreeSpace(slave, project, 0);

        String remote = workspace.getParent().getRemote();
        File remoteDir = new File(remote);

        assertEquals(2000000, getRemoteUsableSpace(workspace)-freeSpace);

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

        freeSpace = spaceLeft.getFreeSpace(slave, project, 0);

        assertEquals(6000000, (getRemoteUsableSpace(workspace)-freeSpace));
    }

    /**
     * Returns the usable disk space from the given file path
     * @param filePath the unit test slave workspace
     * @return the usable disk space
     */
    private Long getRemoteUsableSpace(FilePath filePath) {
        String remote = filePath.getParent().getRemote();
        File remoteDir = new File(remote);

        return Long.valueOf(remoteDir.getUsableSpace());
    }

}
