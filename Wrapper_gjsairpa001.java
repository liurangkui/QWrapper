import java.math.BigDecimal;
import java.sql.Date;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import com.qunar.qfwrapper.bean.search.ProcessResultInfo;
import com.qunar.qfwrapper.bean.search.RoundTripFlightInfo;
import com.qunar.qfwrapper.constants.Constants;
import com.qunar.qfwrapper.interfaces.QunarCrawler;
import com.qunar.qfwrapper.util.QFGetMethod;
import com.qunar.qfwrapper.util.QFHttpClient;

/**
 * 
 * 空蓝航空	往返  
 *@Company Qunar
 *@Team 
 *@author liurangkui
 *@version   V1.0 
 *@date  2014-6-30 下午5:49:45
 */
public class Wrapper_gjsairpa001 implements QunarCrawler {
	private static Logger logger = LoggerFactory.getLogger(Wrapper_gjsairpa001.class);
	public static String places = "KHI,ISB,ABZ,ALC,AMS,AVN,BCN,BRR,BHD,BEB,BGO,EGC,BZR,BHX,BOD,BES,BRS,BUD,CAL,CWL,CMF,CFE,DSA,CFN,DUB,DBV,MME,DUS,EMA,EDI,EXT,FAO,GVA,GLA,GNB,GCI,HAJ,HEL,HUY,INV,ILY,IOM,JER,JYV,KAJ,KEM,KOI,NOC,KOK,LRH,LBA,LIG,LPL,LGW,LTN,LYS,MAD,AGP,MAN,MHQ,MRS,MXP,MUC,NTE,NCL,NQY,NCE,NRK,NWI,NUE,PMI,CDG,ORY,PGF,PRG,RNS,SZG,SVL,SNN,SOF,SOU,BMA,SYY,STR,LSI,TLL,TAY,TRE,TLS,VRK,VRN,VIE,VBY,WAW,WAT,WIC,ZRH";

	public BookingResult getBookingInfo(FlightSearchParam arg0) {

		String bookingUrlPre = "https://www.airblue.com//bookings/flight_selection.aspx";
		BookingResult bookingResult = new BookingResult();
		
		BookingInfo bookingInfo = new BookingInfo();
		bookingInfo.setAction(bookingUrlPre);
		bookingInfo.setMethod("post");
		Map<String, String> map = new LinkedHashMap<String, String>();
		map.put("ro", "0");
		map.put("DC", arg0.getDep());
		map.put("AC", arg0.getArr());
		map.put("cur", "KHI");
		map.put("sdate", arg0.getDepDate().replaceAll("-", "/"));
		map.put("edate", arg0.getDepDate().replaceAll("-", "/"));
		map.put("adult", "1");
		map.put("child", "0");
		map.put("infant", "0");
		map.put("view", "0");
		map.put("btnsubmit", "Flight Search");
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
			
			String getUrl = String.format("https://www.airblue.com//bookings/flight_selection.aspx?TT=RT&DC=%s&AC=%s&AM=%s&AD=%s&RM=%s&RD=%s&FL=on&CC=Y&CD=&PA=1&PC=&PI=&x=48&y=16", searchParam.getDep(), searchParam.getArr(), searchParam.getDepDate().substring(0, 7), searchParam.getDepDate().substring(8, 10), searchParam.getRetDate().substring(0, 7), searchParam.getRetDate().substring(8, 10));
			get = new QFGetMethod(getUrl);
			get.setFollowRedirects(false);
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
		String [] content = StringUtils.substringsBetween(html, "class=\"flight_selection column-count-3 requested-date current-date\">", "</table>");
		//需要有明显的提示语句，才能判断是否INVALID_DATE|INVALID_AIRLINE|NO_RESULT
		/*if (content == null || content.contains("Flights are not available on")) {
			result.setRet(false);
			result.setStatus(Constants.NO_RESULT);
			return result;			
		}*/
		String outInfo = content[0];
		String returnInfo = content[1];
		String [] outFlights = StringUtils.substringsBetween(outInfo, "<tr class=\"flight-status-ontime\">", "</tr>");
		String [] returnFlights = StringUtils.substringsBetween(returnInfo, "<tr class=\"flight-status-ontime\">", "</tr>");
		try {
			List<RoundTripFlightInfo> flightList = new ArrayList<RoundTripFlightInfo>();
			for(String outFlightInfo:outFlights){
				RoundTripFlightInfo baseFlight = new RoundTripFlightInfo();
				//出航航班信息
				List<FlightSegement> segs = new ArrayList<FlightSegement>();
				//返航航班信息
				List<FlightSegement> retSegs = new ArrayList<FlightSegement>();
				//出航航班信息
				List<String> flightNoList = new ArrayList<String>();
				//返航航班信息
				List<String> flightNoRetList = new ArrayList<String>();
				
				FlightDetail flightDetail = new FlightDetail();
				
				
				//航班号
				String go_flight = StringUtils.substringBetween(outFlightInfo, "<td class=\"flight\">", "</td>");
				//起飞时间
				String go_leavTime = StringUtils.substringBetween(outFlightInfo, "<td class=\"time leaving\">", "</td>");
				go_leavTime = toTimeCase(go_leavTime);
				//到达时间
				String go_landTime = StringUtils.substringBetween(outFlightInfo, "<td class=\"time landing\">", "</td>");
				go_landTime = toTimeCase(go_landTime);
				//支付货币单位
				String unit = "";
				//折扣价
				String go_discount = StringUtils.substringBetween(outFlightInfo, "<td rowspan=\"1\" class=\"family family-ED \">", "</td>");
				if(go_discount.contains("Not Available")){
					go_discount = "0.0";
				}else{
					//支付货币单位
					unit = StringUtils.substringBetween(go_discount, "<b>", "</b>").trim();
					go_discount = StringUtils.substringBetween(go_discount, "</b>", "</span>").trim();
					go_discount = go_discount.replace(",", "");
				}
				
				//标准价
				String go_standard = StringUtils.substringBetween(outFlightInfo, "<td rowspan=\"1\" class=\"family family-ES", "</td>");
				if(go_standard.contains("Not Available")){
					go_standard = "0.0";
				}else{
					//支付货币单位
					unit = unit.equals("")?StringUtils.substringBetween(go_standard, "<b>", "</b>").trim():unit;
					go_standard = StringUtils.substringBetween(go_standard, "</b>", "</span>").trim();
					go_standard = go_standard.replace(",", "");
				}
				
				//保险费
				String go_primium = StringUtils.substringBetween(outFlightInfo, "<td rowspan=\"1\" class=\"family family-EP \">", "</td>");
				if(go_primium.contains("Not Available")){
					go_primium = "0.0";
				}else{
					//支付货币单位
					unit = unit.equals("")?StringUtils.substringBetween(go_primium, "<b>", "</b>").trim():unit;
					go_primium = StringUtils.substringBetween(go_primium, "</b>", "</span>").trim();
					go_primium = go_primium.replace(",", "");
				}
				double go_price = 0.0d;
				if(go_discount.equals("0.0")){
					go_price = (new BigDecimal(go_standard)).doubleValue() > (new BigDecimal(go_primium)).doubleValue()?(new BigDecimal(go_primium)).doubleValue():(new BigDecimal(go_standard)).doubleValue();
				}else if(go_standard.equals("0.0")){
					go_price = (new BigDecimal(go_primium)).doubleValue() > (new BigDecimal(go_discount)).doubleValue()?(new BigDecimal(go_discount)).doubleValue():(new BigDecimal(go_primium)).doubleValue();
				}else if (go_primium.equals("0.0")){
					go_price = (new BigDecimal(go_standard)).doubleValue() > (new BigDecimal(go_discount)).doubleValue()?(new BigDecimal(go_discount)).doubleValue():(new BigDecimal(go_standard)).doubleValue();
				}else{
					go_price = (new BigDecimal(go_standard)).doubleValue() > (new BigDecimal(go_discount)).doubleValue()?(new BigDecimal(go_discount)).doubleValue():(new BigDecimal(go_standard)).doubleValue();
					go_price = go_price > (new BigDecimal(go_primium)).doubleValue()?(new BigDecimal(go_primium)).doubleValue():go_price;
				}
				
				String [] go_flightNos = go_flight.split(",");
				for(String flightNo:go_flightNos){
					flightNo = go_flight.replaceAll("[^a-zA-Z\\d]", "").trim();
					flightNoList.add(flightNo);
					
					FlightSegement seg = new FlightSegement();
					seg.setFlightno(flightNo);
					seg.setDepDate(searchParam.getDepDate());
					//seg.setArrDate(searchParam.getDepDate());
					seg.setDepairport(searchParam.getDep());
					seg.setArrairport(searchParam.getArr());
					seg.setDeptime(go_leavTime);
					seg.setArrtime(go_landTime);
					segs.add(seg);
				}
				
				for(String retFlightInfo : returnFlights){
					//航班号
					String ret_flight = StringUtils.substringBetween(retFlightInfo, "<td class=\"flight\">", "</td>");
					//起飞时间
					String ret_leavTime = StringUtils.substringBetween(retFlightInfo, "<td class=\"time leaving\">", "</td>");
					ret_leavTime = toTimeCase(ret_leavTime);
					//到达时间
					String ret_landTime = StringUtils.substringBetween(retFlightInfo, "<td class=\"time landing\">", "</td>");
					ret_landTime = toTimeCase(ret_landTime);
					//支付货币单位
					//String unit = "";
					//折扣价
					String ret_discount = StringUtils.substringBetween(retFlightInfo, "<td rowspan=\"1\" class=\"family family-ED \">", "</td>");
					if(ret_discount.contains("Not Available")){
						ret_discount = "0.0";
					}else{
						//支付货币单位
						unit = StringUtils.substringBetween(ret_discount, "<b>", "</b>").trim();
						ret_discount = StringUtils.substringBetween(ret_discount, "</b>", "</span>").trim();
						ret_discount = ret_discount.replace(",", "");
					}
					
					//标准价
					String ret_standard = StringUtils.substringBetween(retFlightInfo, "<td rowspan=\"1\" class=\"family family-ES", "</td>");
					if(ret_standard.contains("Not Available")){
						ret_standard = "0.0";
					}else{
						//支付货币单位
						unit = unit.equals("")?StringUtils.substringBetween(ret_standard, "<b>", "</b>").trim():unit;
						ret_standard = StringUtils.substringBetween(ret_standard, "</b>", "</span>").trim();
						ret_standard = ret_standard.replace(",", "");
					}
					
					//保险费
					String ret_primium = StringUtils.substringBetween(retFlightInfo, "<td rowspan=\"1\" class=\"family family-EP \">", "</td>");
					if(ret_primium.contains("Not Available")){
						ret_primium = "0.0";
					}else{
						//支付货币单位
						unit = unit.equals("")?StringUtils.substringBetween(ret_primium, "<b>", "</b>").trim():unit;
						ret_primium = StringUtils.substringBetween(ret_primium, "</b>", "</span>").trim();
						ret_primium = ret_primium.replace(",", "");
					}
					double ret_price = 0.0d;
					if(ret_discount.equals("0.0")){
						ret_price = (new BigDecimal(ret_standard)).doubleValue() > (new BigDecimal(ret_primium)).doubleValue()?(new BigDecimal(ret_primium)).doubleValue():(new BigDecimal(ret_standard)).doubleValue();
					}else if(ret_standard.equals("0.0")){
						ret_price = (new BigDecimal(ret_primium)).doubleValue() > (new BigDecimal(ret_discount)).doubleValue()?(new BigDecimal(ret_discount)).doubleValue():(new BigDecimal(ret_primium)).doubleValue();
					}else if (ret_primium.equals("0.0")){
						ret_price = (new BigDecimal(ret_standard)).doubleValue() > (new BigDecimal(ret_discount)).doubleValue()?(new BigDecimal(ret_discount)).doubleValue():(new BigDecimal(ret_standard)).doubleValue();
					}else{
						ret_price = (new BigDecimal(ret_standard)).doubleValue() > (new BigDecimal(ret_discount)).doubleValue()?(new BigDecimal(ret_discount)).doubleValue():(new BigDecimal(ret_standard)).doubleValue();
						ret_price = ret_price > (new BigDecimal(ret_primium)).doubleValue()?(new BigDecimal(ret_primium)).doubleValue():ret_price;
					}
					
					String [] ret_flightNos = ret_flight.split(",");
					for(String flightNo:ret_flightNos){
						flightNo = ret_flight.replaceAll("[^a-zA-Z\\d]", "").trim();
						flightNoRetList.add(flightNo);
						
						FlightSegement seg = new FlightSegement();
						seg.setFlightno(flightNo);
						seg.setDepDate(searchParam.getRetDate());
						//seg.setArrDate(searchParam.getRetDate());
						seg.setDepairport(searchParam.getArr());
						seg.setArrairport(searchParam.getDep());
						seg.setDeptime(ret_leavTime);
						seg.setArrtime(ret_landTime);
						
						retSegs.add(seg);
					}
					double totalPrice = go_price + ret_price;
					
					flightDetail.setDepdate(Date.valueOf(searchParam.getDepDate()));
					flightDetail.setFlightno(flightNoList);
					flightDetail.setMonetaryunit(unit);
					flightDetail.setTax(0.0d);//税费
					flightDetail.setPrice(totalPrice);//机票价格
					flightDetail.setDepcity(searchParam.getDep());
					flightDetail.setArrcity(searchParam.getArr());
					flightDetail.setWrapperid(searchParam.getWrapperid());
					//出航航班价格
					baseFlight.setOutboundPrice(go_price);
					//返航航班价格
					baseFlight.setReturnedPrice(ret_price);
					baseFlight.setRetdepdate(Date.valueOf(searchParam.getRetDate()));
					
					baseFlight.setInfo(segs);
					baseFlight.setRetinfo(retSegs);
					
					baseFlight.setDetail(flightDetail);
					flightList.add(baseFlight);
				}
				
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
