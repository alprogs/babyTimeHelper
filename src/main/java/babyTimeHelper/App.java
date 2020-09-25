package babyTimeHelper;

import java.sql.Date;
import java.text.SimpleDateFormat;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class App {

	private static boolean PRINT_TOOL_FORMAT = true;

	private static Map<String, String> typeTransMap 	= null;
	private static Date today 	= null;
	
	private void init() {
		System.setProperty("sun.net.http.allowRestrictedHeaders", "true");

		typeTransMap 	= new HashMap<>();
		typeTransMap.put( "snack", "간식" );
		typeTransMap.put( "weaning", "이유식" );
		typeTransMap.put( "dried_milk", "분유" );

		today 	= new Date( System.currentTimeMillis() );
	}

	public void doProcess() {
		init();
		
		String activity_list_data	= reqActivityList();
		String activity_recent_data	= reqActivityRecent();

		JsonElement recentActivities 	= parseActivitiesData( activity_recent_data );
		calcLastFeedingTime( recentActivities.getAsJsonObject() );

		JsonElement listActivities 	= parseActivitiesData( activity_list_data );
		calcTodayActivities( listActivities.getAsJsonArray() );
	}

	public String reqActivityList() {
		String messageBody 	= "{\"baby_oid\":\"5d457a1b3bd31d2e176dec0b\",\"size\":50,\"start_align\":true,\"tz\":\"Asia\\/Seoul\"}";
		return sendHttpRequest( "https://babytime.simfler.com/v1/activity/list", messageBody );
	}

	public String reqActivityRecent() {
		String messageBody 	= "{\"filters\":[\"feeding\",\"diaper\",\"sleep\",\"pumping\",\"medicine\"],\"baby_oid\":\"5d457a1b3bd31d2e176dec0b\",\"timestamp\":0}";

		return sendHttpRequest( "https://babytime.simfler.com/v1/activity/recent", messageBody );
	}

	public void calcLastFeedingTime( JsonObject activities ) {
		JsonObject recentFeeding 	= activities.getAsJsonObject( "feeding" );

		String feedingType 		= recentFeeding.get( "type" ).getAsString();
		JsonObject feedingOption	= recentFeeding.get( "option" ).getAsJsonObject();

		int feedingAmount	= feedingOption.get( "amount" ).getAsInt();

		long started_at 	= recentFeeding.get("started_at").getAsLong() * 1000;
		Date activityTime 	= new Date( started_at );

		long timeDiff 		= today.getTime() - activityTime.getTime();

		if (PRINT_TOOL_FORMAT == false) {
			log(String.format("TIME DIFF          : %s", formatDuration( timeDiff )));
			log(String.format("LAST FEEDING       : %s %s ml", typeTransMap.get( feedingType ), feedingAmount));
		} else {
			System.out.print(String.format("%s,%s %s ml", formatDuration( timeDiff ), typeTransMap.get( feedingType ), feedingAmount));
		}
	}

	private static String formatDuration(long duration) {
		long hours = TimeUnit.MILLISECONDS.toHours(duration);
		long minutes = TimeUnit.MILLISECONDS.toMinutes(duration) % 60;
		//long seconds = TimeUnit.MILLISECONDS.toSeconds(duration) % 60;
		//long milliseconds = duration % 1000;
		
		return String.format("%d 시간 %d 분 전", hours, minutes);
	}

	private void calcTodayActivities(JsonArray activities) {
		int totalFeeding 	= 0;
		int totalDriedMilk 	= 0;
		int totalWeaning 	= 0;

		JsonArray todayActivities 	= getTodayActivities( activities );

		for (JsonElement activityElem: todayActivities) {
			JsonObject activity 	= activityElem.getAsJsonObject();

			String type 		= activity.get( "type" ).getAsString();
			JsonObject option 	= activity.get( "option" ).getAsJsonObject();

			int amount 			= 0;
			if (option.get( "amount" ) != null) {
				amount 			= option.get( "amount" ).getAsInt();
			}

			if (type.equals( "dried_milk" )) {
				totalDriedMilk 	+= amount;

			} else if (type.equals( "weaning" )) {
				totalWeaning 	+= amount;
			}
		}

		totalFeeding 	= totalDriedMilk + totalWeaning;
		
		if (PRINT_TOOL_FORMAT == false) {
			log("TOTAL FEEDING      : "+ totalFeeding +" ml ( "+ totalDriedMilk +" + "+ totalWeaning +" )");
		} else {
			//System.out.println(String.format(",%s ml (%s + %s)", totalFeeding, totalDriedMilk, totalWeaning));
			System.out.println(String.format(",%s,%s,%s", totalFeeding, totalDriedMilk, totalWeaning));
		}
	}

	private JsonElement parseActivitiesData( String responseMessage ) {
		
		JsonElement activities 	= null;

		try {
			JsonParser parser 	= new JsonParser();
			JsonObject root 	= (JsonObject) parser.parse( responseMessage );
			JsonObject data 	= root.getAsJsonObject( "data" );

			activities 			= data.get( "activities" );

		} catch ( Exception e ) {
			log( e );
		}

		return activities;
	}

	private JsonArray getTodayActivities(JsonArray activities) {
		JsonArray activities_tmp 	= new JsonArray();

		for (JsonElement activityElem: activities) {
			JsonObject activity 	= activityElem.getAsJsonObject();

			long started_at 	= activity.get("started_at").getAsLong() * 1000;
			Date activityTime 	= new Date( started_at );

			long noOfDaysBetween 	= ChronoUnit.DAYS.between( activityTime.toLocalDate(), today.toLocalDate() );
			
			if (noOfDaysBetween == 0) {
				activities_tmp.add( activity );
			}
		}

		return activities_tmp;
	}

	private void printActivity( JsonObject activity ) {
		log( "==================================================");
		String type 		= activity.get("type").getAsString();

		JsonObject option 	= activity.getAsJsonObject("option");

		int amount 			= 0;
		String snackType 	= "";
		
		if ( type.equals("dried_milk") || type.equals("weaning") ) {
			amount 	= option.get("amount").getAsInt();
		} else if ( type.equals("snack") ) {
			snackType 	= option.get("snack_type").getAsString();
		}

		long started_at 	= activity.get("started_at").getAsLong() * 1000;

		Date activityTime 	= new Date( started_at );
		SimpleDateFormat 	prettyTimeTag 	= new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

		String info 	= (snackType.length() == 0)? String.valueOf( amount ):snackType;

		log("TYPE: "+ typeTransMap.get( type ));
		log("INFO: "+ info);
		log("TIME: "+ prettyTimeTag.format( activityTime ));
	}

	public String sendHttpRequest(String urlStr, String messageBody){
		String responseBody 	= "NOT ASSIGNED";

		try {
			OkHttpClient client = new OkHttpClient().newBuilder().build();

			MediaType mediaType = MediaType.parse("text/plain");

			RequestBody body = RequestBody.create(mediaType, messageBody);

			Request request = new Request.Builder()
				.url( urlStr )
				.method("POST", body)
				.addHeader("User-Agent", "Android SM-N960N")
				.addHeader("bt-pf", "android")
				.addHeader("bt-pf-ver", "10")
				.addHeader("bt-app-ver", "310")
				.addHeader("bt-udid", "6288347c-6c87-4fa0-b261-552cf302db9f")
				.addHeader("bt-lang", "ko_KR")
				.addHeader("Host", "babytime.simfler.com")
				.addHeader("Connection", "Keep-Alive")
				.addHeader("Accept-Encoding", "gzip")
				.addHeader("Content-Length", "113")
				.addHeader("Cookie", "token=\"2|1:0|10:1601008336|5:token|168:eyJfaWQiOiAiNWQ0NTc5YzMxYTlkNzg1MWE0OWQyMDc4IiwgInAiOiAiNTAxM2JlYWU5MzkyZmE3NWNiMGFmNjJmZTk5ZjliZDUxMjA3ODZmNDQ4NTZkMjE2MmE2ZmJkNDIiLCAiZSI6IDI1NDcwODgzMzYuMTA0NTYyfQ==|84d59581fa45e894c91541462e597445bf2f3d9d089990d9cf48d83c9a5c2d0f\"")
				.addHeader("Content-Type", "text/plain")
				.build();

			Response response 	= client.newCall(request).execute();
			responseBody 		= response.body().string();

		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return responseBody;
	}

	private void log(Exception e) {
		printSTEInfo( Thread.currentThread().getStackTrace()[2], e.getMessage() );
	}

	private void log(String message) {
		printSTEInfo( Thread.currentThread().getStackTrace()[2], message );
	}

	private void printSTEInfo(StackTraceElement ste, String message) {
		String className 		= ste.getClassName();
		className 	= className.substring( className.lastIndexOf(".") +1, className.length());

		StringBuilder sb = new StringBuilder();
		sb.append( String.format("[%-15s][%-15s][%-3s] ", className, ste.getMethodName(), ste.getLineNumber()) );
		sb.append(message);

		System.out.println( sb.toString() );
	}

    public static void main(String[] args) {
		App proc = new App();
		
		proc.doProcess();
	}
}

