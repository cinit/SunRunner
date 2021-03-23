package das.lazy.sunrunner.data;

import org.json.JSONException;
import org.json.JSONObject;

public class RunRecord implements Comparable<RunRecord> {

    public String costTime;
    public int costDistance;
    public int avaLength;
    public String resultDate;
    public int resultHour;
    public int stepNum;
    public int notCountReason;
    public float speed;

    public RunRecord() {
    }

    public RunRecord(JSONObject o) throws JSONException {
        costTime = o.getString("CostTime");
        costDistance = (int) o.getDouble("CostDistance");
        avaLength = (int) o.getDouble("AvaLengths");
        resultDate = o.getString("ResultDate");
        resultHour = o.getInt("ResultHour");
        stepNum = o.getInt("StepNum");
        if (o.get("NoCountReason") == JSONObject.NULL) {
            notCountReason = 0;
        } else {
            notCountReason = Integer.parseInt(o.getString("NoCountReason"));
        }
        speed = (float) o.getDouble("Speed");
    }

    @Override
    public String toString() {
        return "RunRecord{" +
                "costTime='" + costTime + '\'' +
                ", costDistance=" + costDistance +
                ", avaLength=" + avaLength +
                ", resultDate='" + resultDate + '\'' +
                ", resultHour=" + resultHour +
                ", stepNum=" + stepNum +
                ", notCountReason=" + notCountReason +
                ", speed=" + speed +
                '}';
    }

    @Override
    public int compareTo(RunRecord o) {
        int thiz = Integer.parseInt(resultDate.replaceAll("[年月日]", "")) * 100 + resultHour;
        int that = Integer.parseInt(o.resultDate.replaceAll("[年月日]", "")) * 100 + o.resultHour;
        return that - thiz;
    }
}
