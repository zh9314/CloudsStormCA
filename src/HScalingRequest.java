
public class HScalingRequest {
	public String reqID;
	
	////These two are the target datacenter, where this sub-topology is recovered. 
	public String cloudProvider;
	public String dataCentre;
	
	////identify the name of the scaled topology. If it is null, this will be auto generated.
	public String scaledSTName;
	
	///Identify the objects' name that needs to be scaled. There are '||' inside.
	public String targetObjects;
	
	public String targetObjectType;
	
	///it means scaling out/up
	public String scalingDirection;
}
