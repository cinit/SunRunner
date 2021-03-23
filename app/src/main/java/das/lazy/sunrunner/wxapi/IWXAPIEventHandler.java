package das.lazy.sunrunner.wxapi;

import das.lazy.sunrunner.wxapi.cmd.BaseReq;
import das.lazy.sunrunner.wxapi.cmd.BaseResp;

public interface IWXAPIEventHandler {
    void onReq(BaseReq baseReq);

    void onResp(BaseResp baseResp);
}
