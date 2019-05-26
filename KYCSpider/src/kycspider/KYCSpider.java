package kycspider;

import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.WebElement;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.google.gson.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map; 


public class KYCSpider {
	public ArrayList<String> newURLs = new ArrayList<>();
	public ArrayList<String> oldURLs = new ArrayList<>();
	public ArrayList<JSONObject> oldData = new ArrayList<>();  
	public boolean isNewDoc = false;
	public WebDriver driver;
	public JSONArray newData = new JSONArray();
	
	public static void main(String[] args) {
		KYCSpider s = new KYCSpider();
		System.setProperty("webdriver.gecko.driver", "lib/geckodriver.exe");
		
	    s.createDataJson();
		
		s.driver = new FirefoxDriver();
	    s.driver.get("http://www.vlada.si/en/about_the_government/members_of_government/");
		
	    s.crawlMembersList();	    
	} //main
	
	public void crawlMembersList() {
		List<WebElement> members = driver.findElements(By.cssSelector("div.c4.bp2"));
		for (int i = 0; i < members.size(); i++) {
			WebElement m = members.get(i);
			String name = "", designation = "", dob = "", address = "", email = "", phone = "", 
					website = "", sourceURL = "", operation = "";

			designation = m.findElement(By.cssSelector("h6")).getText();
			
			WebElement link = m.findElement(By.cssSelector("a.internal-link"));
			name = link.getText();
			link.click();
			
			sourceURL = driver.getCurrentUrl();
			newURLs.add(sourceURL);
			
			WebElement c3right = driver.findElement(By.cssSelector("div.c3.right"));
			email = c3right.findElement(By.xpath(".//a[contains(@href, 'javascript')]")).getText();
			
			
			List<WebElement> f = c3right.findElements(By.cssSelector("p.bodytext"));
			ArrayList<String> strings = new ArrayList<>();
			f.forEach(e -> {
				String x = e.getText();
				x = x.trim();
				String[] temp = x.split("\n");
				for (String y : temp) {
					strings.add(y);
				}
			});
			if(strings.get(0).toLowerCase().contains("office") || strings.get(0).toLowerCase().contains("ministry")) {
				strings.remove(0);
			}
			for(String x : strings) {
				if(x.contains("T:")) {
					phone = x.substring(phone.indexOf("T:") + 3).trim();
				}
				if(x.contains("W:")) {
					website = x.substring(phone.indexOf("W:") + 3).trim();
				}
			}
			
			address = strings.get(0);
			
			dob = getDOB();
			
			operation = "Unmodified";
			if(isNewDoc == true) {
				operation = "New";
			} else {
				boolean found = false;
				for(JSONObject j : oldData) {
					if (sourceURL.equals(j.get("sourceURL").toString())) {
						found = true;
						if (
								name.equals(j.get("name").toString()) == false ||
								designation.equals(j.get("designation").toString()) == false ||
								dob.equals(j.get("dob").toString()) == false ||
								address.equals(j.get("address").toString()) == false ||
								email.equals(j.get("email").toString()) == false ||
								phone.equals(j.get("phone").toString()) == false ||
								website.equals(j.get("website").toString()) == false
								) {
							operation = "Changed";
						}
					}
				}
				if (found == false) operation = "New";
			}
			
			Map<String, String> map = new LinkedHashMap<String, String>(9);
			map.put("name", name);
			map.put("designation", designation);
			map.put("dob", dob);
			map.put("address", address);
			map.put("email", email);
			map.put("phone", phone);
			map.put("website", website);
			map.put("sourceURL", sourceURL);
			map.put("operation", operation);
						
			newData.add(map);
			
			driver.navigate().back();
			members = driver.findElements(By.cssSelector("div.c4.bp2"));
		} //for
		writeToJSON();
		driver.quit();
	}
	
	public void writeToJSON() {
		checkDeleted();
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		JsonParser jp = new JsonParser();
		JsonElement je = jp.parse(newData.toJSONString());
		String prettyJsonString = gson.toJson(je);
		try {
			File fw = new File("data.json");
			Writer out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fw), "UTF8"));
			out.write(prettyJsonString);
			out.flush();
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public String getDOB() {
		String dob = "";
		List<WebElement> g = driver.findElement(By.cssSelector("div.c9.main")).findElements(By.className("bodytext"));
		for (int i = 0; i < g.size(); i++) {
			WebElement we = g.get(i);
			try {
				we = we.findElement(By.cssSelector("span"));
			} catch (NoSuchElementException e) {
				g.remove(i);
			}
		}
        String[] monthsArray = {"january", "february","march","april","may","june","july",
                  "august","september","october","november","december"};
        List<String> months = Arrays.asList(monthsArray);
        
		ArrayList<String> strings = new ArrayList<>();
        g.forEach(e -> strings.add(e.getText().toLowerCase()));
        for (String s : strings) {
            if (s.contains("born")) {
            	String day = "", month = "", year = "";
                String[] birth = s.split(" ");
                for (String s2 : birth) {
                    String string = s2.trim();
                    if (isNumber(string)) { //year or date
                    	if(string.length() == 4) {
                            year = string;
                        } else if (string.length() == 1) { //single digit date
                            day = "0" + string;
                        } else {
                        	day = string;
                        }
                    } else if (months.contains(string)) { //is a month
                        month = String.valueOf(months.indexOf(string) + 1);
                        if (month.length() == 1) {
                            month = "0" + month;
                        }
                    }
                }
                dob = month + "/" + day + "/" + year;  
            }
        }
        return dob;
	}
	
	public void checkDeleted() {
		for(JSONObject j : oldData) {
			if(newURLs.contains(j.get("sourceURL").toString()) == false) {
				Map<String, String> map = new LinkedHashMap<String, String>(9);
				map.put("name", j.get("name").toString());
				map.put("designation", j.get("designation").toString());
				map.put("address", j.get("address").toString());
				map.put("email", j.get("email").toString());
				map.put("phone", j.get("phone").toString());
				map.put("website", j.get("website").toString());
				map.put("dob", j.get("dob").toString());
				map.put("sourceURL", j.get("sourceURL").toString());
				map.put("operation", "Deleted");
				newData.add(map);
			}
		}
	}
	
	public static boolean isNumber(String s) {
        try {
            Double num = Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
	}
	
	public void createDataJson() {
		File dataFile = new File("data.json");
		if(dataFile.exists() == false) { //if no data.json file, create one
			isNewDoc = true;
			try {
				dataFile.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			} //try
		} else { //else read the data into oldData
			JSONParser parser = new JSONParser();
			try {
		        JSONArray oldArray = (JSONArray)parser.parse(new InputStreamReader(new FileInputStream("data.json"), StandardCharsets.UTF_8));
		        setOldURLs(oldArray);
			} catch (Exception e) {
	            e.printStackTrace();
			} //try
		} //if
	} //createDataJson
	
	public void setOldURLs(JSONArray oldArray) {
		for(int i = 0; i < oldArray.size(); i++) {
			JSONObject jo = (JSONObject)oldArray.get(i);
			oldData.add(jo);
			oldURLs.add(jo.get("sourceURL").toString());
		}
	}
} //KYCSpider