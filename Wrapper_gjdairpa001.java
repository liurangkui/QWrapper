import java.math.BigDecimal;
import java.sql.Date;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.cookie.CookiePolicy;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.serializer.SerializerFeature;
import com.qunar.qfwrapper.bean.booking.BookingInfo;
import com.qunar.qfwrapper.bean.booking.BookingResult;
import com.qunar.qfwrapper.bean.search.FlightDetail;
import com.qunar.qfwrapper.bean.search.FlightSearchParam;
import com.qunar.qfwrapper.bean.search.FlightSegement;
import com.qunar.qfwrapper.bean.search.OneWayFlightInfo;
import com.qunar.qfwrapper.bean.search.ProcessResultInfo;
import com.qunar.qfwrapper.constants.Constants;
import com.qunar.qfwrapper.interfaces.QunarCrawler;
import com.qunar.qfwrapper.util.QFGetMethod;
import com.qunar.qfwrapper.util.QFHttpClient;
import com.qunar.qfwrapper.util.QFPostMethod;

/**
*空蓝航空单程
*/
public class Wrapper_gjdairpa001 implements QunarCrawler{
	private static Logger logger = LoggerFactory.getLogger(Wrapper_gjdairpa001.class);
	public static String places = "KHI,ISB,ABZ,ALC,AMS,AVN,BCN,BRR,BHD,BEB,BGO,EGC,BZR,BHX,BOD,BES,BRS,BUD,CAL,CWL,CMF,CFE,DSA,CFN,DUB,DBV,MME,DUS,EMA,EDI,EXT,FAO,GVA,GLA,GNB,GCI,HAJ,HEL,HUY,INV,ILY,IOM,JER,JYV,KAJ,KEM,KOI,NOC,KOK,LRH,LBA,LIG,LPL,LGW,LTN,LYS,MAD,AGP,MAN,MHQ,MRS,MXP,MUC,NTE,NCL,NQY,NCE,NRK,NWI,NUE,PMI,CDG,ORY,PGF,PRG,RNS,SZG,SVL,SNN,SOF,SOU,BMA,SYY,STR,LSI,TLL,TAY,TRE,TLS,VRK,VRN,VIE,VBY,WAW,WAT,WIC,ZRH";

	
	public BookingResult getBookingInfo(FlightSearchParam param) {
		String bookingUrlPre = "https://www.airblue.com//bookings/flight_selection.aspx";
		BookingResult bookingResult = new BookingResult();
		BookingInfo bookingInfo = new BookingInfo();
		bookingInfo.setAction(bookingUrlPre);
		bookingInfo.setMethod("get");
		Map<String, String> map = new LinkedHashMap<String, String>();
		map.put("TT", "OW");
		/*map.put("SS", "");
		map.put("RT", "");*/
		map.put("FL", "on");
		map.put("DC", param.getDep());
		map.put("AC", param.getArr());
		map.put("AM", param.getDepDate().substring(0, 7));
		map.put("AD", param.getDepDate().substring(8, 10));
		/*map.put("DC", "");
		map.put("AC", "");
		map.put("AM", "");
		map.put("AD", "");
		map.put("DC", "");
		map.put("AC", "");
		map.put("AM", "");
		map.put("AD", "");
		map.put("DC", "");
		map.put("AC", "");
		map.put("AM", "");
		map.put("AD", "");
		map.put("RM", "");
		map.put("RD", "");*/
		map.put("PA", "1");
		/*map.put("PC", "");
		map.put("PI", "");
		map.put("CC", "");
		map.put("NS", "");
		map.put("CD", "");*/
		bookingInfo.setInputs(map);		
		bookingResult.setData(bookingInfo);
		bookingResult.setRet(true);
		return bookingResult;

	}
	
	
	public String getHtml(FlightSearchParam searchParam){
		QFGetMethod get = null;	
		try {
			QFHttpClient httpClient = new QFHttpClient(searchParam, false);
			httpClient.getParams().setCookiePolicy(CookiePolicy.BROWSER_COMPATIBILITY);
			
			String getUrl = String.format("https://www.airblue.com//bookings/flight_selection.aspx?TT=OW&SS=&RT=&FL=on&DC=%s&AC=%s&AM=%s&AD=%s&DC=&AC=&AM=&AD=&DC=&AC=&AM=&AD=&DC=&AC=&AM=&AD=&RM=&RD=&PA=1&PC=&PI=&CC=Y&NS=&CD=", searchParam.getDep(), searchParam.getArr(), searchParam.getDepDate().substring(0, 7), searchParam.getDepDate().substring(8, 10));
			get = new QFGetMethod(getUrl);
		    int status = httpClient.executeMethod(get);
		    return get.getResponseBodyAsString();
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		} finally{
			if (null != get){
				get.releaseConnection();
			}
		}
		return "Exception";
	}
	
	public ProcessResultInfo process(String html,FlightSearchParam searchParam){
		
		ProcessResultInfo result = new ProcessResultInfo();
		if ("Exception".equals(html)) {
			result.setRet(false);
			result.setStatus(Constants.CONNECTION_FAIL);
			return result;			
		}
		
		//需要有明显的提示语句，才能判断是否INVALID_DATE|INVALID_AIRLINE|NO_RESULT
		if (html.contains("Flights are not available on the dates selected")) {
			result.setRet(false);
			result.setStatus(Constants.INVALID_DATE);
			return result;			
		}
		String content = StringUtils.substringBetween(html, "class=\"flight_selection column-count-3 requested-date current-date\">", "</table>");
		//需要有明显的提示语句，才能判断是否INVALID_DATE|INVALID_AIRLINE|NO_RESULT
		if (content == null || content.contains("Flights are not available on")) {
			result.setRet(false);
			result.setStatus(Constants.NO_RESULT);
			return result;			
		}
		String [] jsonStrs = StringUtils.substringsBetween(content, "<tr class=\"flight-status-ontime\">", "</tr>");
		String date = "";//起飞日期
		String org = "";//起飞机场三字码
		String dst = "";//到达机场三字码
		
		String tempStr = "";
		try {			
			List<OneWayFlightInfo> flightList = new ArrayList<OneWayFlightInfo>();
			for(int i = 0; i < jsonStrs.length; i++){
				OneWayFlightInfo baseFlight = new OneWayFlightInfo();
				List<FlightSegement> segs = new ArrayList<FlightSegement>();
				FlightDetail flightDetail = new FlightDetail();
				FlightSegement seg = new FlightSegement();
				List<String> flightNoList = new ArrayList<String>();
				
				tempStr = jsonStrs[i];
				//航班号
				String flight = StringUtils.substringBetween(tempStr, "<td class=\"flight\">", "</td>");
				//起飞时间
				String leavTime = StringUtils.substringBetween(tempStr, "<td class=\"time leaving\">", "</td>");
				leavTime = toTimeCase(leavTime);
				//到达时间
				String landTime = StringUtils.substringBetween(tempStr, "<td class=\"time landing\">", "</td>");
				landTime = toTimeCase(landTime);
				//支付货币单位
				String unit = "";
				//折扣价
				String discount = StringUtils.substringBetween(tempStr, "<td rowspan=\"1\" class=\"family family-ED \">", "</td>");
				if(discount.contains("Not Available")){
					discount = "0.0";
				}else{
					//支付货币单位
					unit = StringUtils.substringBetween(discount, "<b>", "</b>").trim();
					discount = StringUtils.substringBetween(discount, "</b>", "</span>").trim();
					discount = discount.replace(",", "");
				}
				
				//标准价
				String standard = StringUtils.substringBetween(tempStr, "<td rowspan=\"1\" class=\"family family-ES", "</td>");
				if(standard.contains("Not Available")){
					standard = "0.0";
				}else{
					//支付货币单位
					unit = unit.equals("")?StringUtils.substringBetween(standard, "<b>", "</b>").trim():unit;
					standard = StringUtils.substringBetween(standard, "</b>", "</span>").trim();
					standard = standard.replace(",", "");
				}
				
				//保险费
				String primium = StringUtils.substringBetween(tempStr, "<td rowspan=\"1\" class=\"family family-EP \">", "</td>");
				if(primium.contains("Not Available")){
					primium = "0.0";
				}else{
					//支付货币单位
					unit = unit.equals("")?StringUtils.substringBetween(standard, "<b>", "</b>").trim():unit;
					primium = StringUtils.substringBetween(primium, "</b>", "</span>").trim();
					primium = primium.replace(",", "");
				}
				double price = 0.0d;
				if(discount.equals("0.0")){
					price = (new BigDecimal(standard)).doubleValue() > (new BigDecimal(primium)).doubleValue()?(new BigDecimal(primium)).doubleValue():(new BigDecimal(standard)).doubleValue();
				}else if(standard.equals("0.0")){
					price = (new BigDecimal(primium)).doubleValue() > (new BigDecimal(discount)).doubleValue()?(new BigDecimal(discount)).doubleValue():(new BigDecimal(primium)).doubleValue();
				}else if (primium.equals("0.0")){
					price = (new BigDecimal(standard)).doubleValue() > (new BigDecimal(discount)).doubleValue()?(new BigDecimal(discount)).doubleValue():(new BigDecimal(standard)).doubleValue();
				}else{
					price = (new BigDecimal(standard)).doubleValue() > (new BigDecimal(discount)).doubleValue()?(new BigDecimal(discount)).doubleValue():(new BigDecimal(standard)).doubleValue();
					price = price > (new BigDecimal(primium)).doubleValue()?(new BigDecimal(primium)).doubleValue():price;
				}
				
				//String flightNo = flight.replaceAll("[^a-zA-Z\\d]", "");
				String [] flightNos = flight.split(",");
				for(String flightNo:flightNos){
					flightNo = flight.replaceAll("[^a-zA-Z\\d]", "");
					flightNoList.add(flightNo);
					seg.setFlightno(flightNo);
					seg.setDepDate(searchParam.getDepDate());
					seg.setArrDate(searchParam.getDepDate());
					seg.setDepairport(searchParam.getDep());
					seg.setArrairport(searchParam.getArr());
					seg.setDeptime(leavTime);
					seg.setArrtime(landTime);
				}
				
				flightDetail.setDepdate(Date.valueOf(searchParam.getDepDate()));
				flightDetail.setFlightno(flightNoList);
				flightDetail.setMonetaryunit(unit);
				flightDetail.setTax(0.0d);//税费
				flightDetail.setPrice(price);//机票价格
				flightDetail.setDepcity(searchParam.getDep());
				flightDetail.setArrcity(searchParam.getArr());
				flightDetail.setWrapperid(searchParam.getWrapperid());
				segs.add(seg);
				baseFlight.setDetail(flightDetail);
				baseFlight.setInfo(segs);
				flightList.add(baseFlight);
			}
			result.setRet(true);
			result.setStatus(Constants.SUCCESS);
			result.setData(flightList);
			return result;
		} catch(Exception e){
			logger.error(e.getMessage(), e);
			System.out.println(e);
			result.setRet(false);
			result.setStatus(Constants.PARSING_FAIL);
			return result;
		}
	}
	/**
	 * 将12小时制转换为24小时制
	 * @param time 如：07:20 下午
	 * @return
	 */
	public static String toTimeCase(String time){
		String format12 = "hh:mm a";
		String format24 = "HH:mm";
		DateFormat dateFormat12 = new SimpleDateFormat(format12);
        DateFormat dateFormat24 = new SimpleDateFormat(format24);
        
		time = time.toUpperCase();
        if(time.contains("AM")){
        	time = time.replace("AM", "").trim();
        }else if(time.contains("PM")){
        	time = time.replace("PM", "下午");
        }
        
        try {
          java.util.Date date = null;
          date = dateFormat12.parse(time);
          time = dateFormat24.format(date);
        } catch (ParseException e) {
        	logger.error(e.getMessage(), e);
        }
		return time;
	}
    
   

}
