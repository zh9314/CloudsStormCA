import commonTool.Log4JUtils;
import lambdaExprs.infrasCode.main.ICYAML;

public class ExeThread extends Thread {
	private ICYAML icYaml = null;
	private String logPath = null;
	private String appID = null;
	
	public ExeThread(ICYAML ic, String logP, String appId){
		icYaml = ic;
		logPath = logP;
		appID = appId;
	}
	
	@Override
	public void run(){
		icYaml.run(logPath);
		
		Log4JUtils.removeAllLogAppender();

		ExeOperation.releaseSignal(appID);
	}
	
	
}
