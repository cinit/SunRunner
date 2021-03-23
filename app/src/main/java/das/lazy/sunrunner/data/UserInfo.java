package das.lazy.sunrunner.data;

import com.qq.taf.jce.JceInputStream;
import com.qq.taf.jce.JceOutputStream;
import com.qq.taf.jce.JceStruct;
import das.lazy.sunrunner.util.JceId;

public class UserInfo extends JceStruct {
    @JceId(0) public int uid = -1;
    @JceId(1) public String nickName = "";
    @JceId(2) public String userName = "";
    @JceId(3) public int isSchoolMode;
    @JceId(4) public String schoolId = "";
    @JceId(5) public String schoolName = "";
    @JceId(6) public float schoolRunMinSpeed;
    @JceId(7) public float schoolRunMaxSpeed;
    @JceId(8) public int schoolRunLength;

    @Override
    public void writeTo(JceOutputStream os) {
        os.write(uid, 0);
        os.write(nickName, 1);
        os.write(userName, 2);
        os.write(isSchoolMode, 3);
        if (isSchoolMode == 1) {
            os.write(schoolId, 4);
            os.write(schoolName, 5);
            os.write(schoolRunMinSpeed, 6);
            os.write(schoolRunMaxSpeed, 7);
            os.write(schoolRunLength, 8);
        }
    }

    @Override
    public void readFrom(JceInputStream is) {
        uid = is.read(-1, 0, true);
        nickName = is.read("", 1, true);
        userName = is.read("", 2, true);
        isSchoolMode = is.read(-1, 3, true);
        if (isSchoolMode == 1) {
            schoolId = is.read("", 4, true);
            schoolName = is.read("", 5, true);
            schoolRunMinSpeed = is.read(0.0f, 6, true);
            schoolRunMaxSpeed = is.read(0.0f, 7, true);
            schoolRunLength = is.read(0, 8, true);
        }
    }

    @Override
    public String toString() {
        return "UserInfo{" +
                "uid=" + uid +
                ", nickName='" + nickName + '\'' +
                ", userName='" + userName + '\'' +
                ", isSchoolMode=" + isSchoolMode +
                ", schoolId='" + schoolId + '\'' +
                ", schoolName='" + schoolName + '\'' +
                ", schoolRunMinSpeed=" + schoolRunMinSpeed +
                ", schoolRunMaxSpeed=" + schoolRunMaxSpeed +
                ", schoolRunLength=" + schoolRunLength +
                '}';
    }
}
