import lambdaExprs.infrasCode.main.ICYAML;

public class ExeThread extends Thread {
	private ICYAML icYaml = null;
	private String logPath = null;
	
	public ExeThread(ICYAML ic, String logP){
		icYaml = ic;
		logPath = logP;
	}
	
	@Override
	public void run(){
		icYaml.run(logPath);
	}
}
