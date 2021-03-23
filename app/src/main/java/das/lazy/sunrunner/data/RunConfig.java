package das.lazy.sunrunner.data;

import com.qq.taf.jce.JceInputStream;
import com.qq.taf.jce.JceOutputStream;
import com.qq.taf.jce.JceStruct;

public class RunConfig extends JceStruct {

    public double latitude = 31.93178;
    public double longitude = 118.8865121;

    public int maxRunTail = 6;

    public int minRunTime = 800;
    public int maxRunTime = 1700;

    public int minRunSteps = 1024;
    public int maxRunSteps = 2333;

    @Override
    public void writeTo(JceOutputStream os) {

    }

    @Override
    public void readFrom(JceInputStream is) {

    }
}
