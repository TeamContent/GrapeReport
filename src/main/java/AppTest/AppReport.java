package AppTest;

import httpServer.booter;
import nlogger.nlogger;

public class AppReport {
	public static void main(String[] args) {
		booter booter = new booter();
		try {
			System.out.println("GrapeReport");
			System.setProperty("AppName", "GrapeReport");
			booter.start(1006);
		} catch (Exception e) {
			nlogger.logout(e);
		}
	}
}
