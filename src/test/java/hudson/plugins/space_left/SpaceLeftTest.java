package hudson.plugins.space_left;

import hudson.model.*;
import hudson.model.labels.LabelAtom;
import hudson.model.queue.CauseOfBlockage;
import hudson.slaves.DumbSlave;
import hudson.slaves.SlaveComputer;
import junit.framework.TestCase;
import org.junit.Test;
import org.jvnet.hudson.test.HudsonTestCase;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: jet
 * Date: 4/5/12
 * Time: 2:14 PM
 * To change this template use File | Settings | File Templates.
 */
public class SpaceLeftTest extends HudsonTestCase {


    @Test
    public void testGetFreeSpace() throws Exception {
        // init slave
        LabelAtom label = new LabelAtom("label");
        DumbSlave slave = this.createSlave(label);
        SlaveComputer c = slave.getComputer();
        c.connect(false).get(); // wait until it's connected
        if(c.isOffline()) {
            fail("Slave failed to go online: "+c.getLog());
        }

        SpaceLeft spaceLeft = new SpaceLeft();

        Long freeSpace = spaceLeft.getFreeSpace(slave, null, -1L);

        assertTrue(freeSpace > 0L);

        // get free space from empty slave
        SpaceLeftQueueTaskDispatcher spaceLeftQueueTaskDispatcher = new SpaceLeftQueueTaskDispatcher();


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

        //assertEquals(freeSpace/100000L - 10L, otherFreeSpace/100000L);
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

        //assertEquals(freeSpace/100000L - 30L, otherFreeSpace / 100000L);
        assertTrue((Math.abs(otherFreeSpace - (freeSpace - 3000000L)) < 100000L) );

        StringParameterValue value = new StringParameterValue("REQUIRED_SPACE", "3000000");
        List<ParameterValue> params = new ArrayList<ParameterValue>();
        params.add(value);
        item.addAction(new ParametersAction(params));

        otherFreeSpace = spaceLeft.getFreeSpace(slave, project, 3000000L);

        //assertEquals(freeSpace/100000L - 50L, otherFreeSpace / 100000L);

        assertTrue((Math.abs(otherFreeSpace - (freeSpace - 5000000L)) < 100000L) );

    }

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
}
