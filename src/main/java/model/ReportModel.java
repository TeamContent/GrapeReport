package model;

import java.io.File;
import java.net.URLDecoder;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Base64.Decoder;
import java.util.Map.Entry;

import org.apache.commons.collections4.Get;
import org.bson.types.ObjectId;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.mongodb.util.JSON;
import com.mysql.fabric.xmlrpc.base.Array;

import apps.appsProxy;
import database.db;
import esayhelper.DBHelper;
import esayhelper.JSONHelper;
import esayhelper.StringHelper;
import esayhelper.TimeHelper;
import esayhelper.checkHelper;
import esayhelper.fileHelper;
import esayhelper.formHelper;
import esayhelper.jGrapeFW_Message;
import esayhelper.formHelper.formdef;
import filterword.WordFilter;
import interfaceApplication.ReportGroup;
import interrupt.interrupt;
import nlogger.nlogger;
import offices.excelHelper;
import rpc.execRequest;
import security.codec;
import session.session;
import sms.ruoyaMASDB;

public class ReportModel {
	private static DBHelper report;
	private static formHelper form;
	private JSONObject _obj = new JSONObject();
	private static int appid = appsProxy.appid();
	private String host = "123.57.214.226:801";
	private List<String> imgList = new ArrayList<>();
	private List<String> videoList = new ArrayList<>();
	// private wechatHelper helper = new wechatHelper("wx604cf5644f5c216b",
	// "f93d37ac273c3b313850848d771e5e27");

	static {
		report = ReportModel.getdb();
		form = report.getChecker();
	}

	private static DBHelper getdb() {
		return getdb(appsProxy.configValue());
	}

	private static DBHelper getdb(JSONObject threadifo) {
		System.out.println(threadifo);
		DBHelper tmpdb = new DBHelper(threadifo.get("db").toString(), "reportInfo", "_id");
		tmpdb.bind(String.valueOf(appid));
		return tmpdb;
	}

	private db bind() {
		return report.bind(appid + "");
	}

	// 新增
	public String Add(String infos) {
		JSONObject object = JSONHelper.string2json(infos);
		if ((object.get("content").toString().length() > 500)) {
			nlogger.logout("length" + object.get("content").toString().length());
			return resultMessage(6);
		}
		if (("").equals(object.get("userid").toString())) {
			return resultMessage(15);
		}
		int mode = Integer.parseInt(object.get("mode").toString());
		String info = "";
		if (mode == 0) {
			// 判断用户是否被封号
			String message = appsProxy
					.proxyCall(host, appid + "/16/wechatUser/FindOpenId/oZU2Lw7s_7bATZXXJL5L2CvmFrCY", null, "")
					.toString();
			String msg = JSONHelper.string2json(message).get("message").toString();
			if (("").equals(msg)) {
				session session = new session();
				session.setget(object.get("userid").toString(), object.toString());
				return resultMessage(12);
			}
			if (("1").equals(JSONHelper.string2json(msg).get("isdelete").toString())) {
				return resultMessage(9);
			}

			info = RealName(object);
			if (info == null) {
				System.out.println("www");
			}
		}
		if (mode == 1) {
			setCheck();
			if (!form.checkRuleEx(object)) {
				return resultMessage(1);
			}
			info = insert(object);
			if (info == null) {
				info = resultMessage(99);
			} else {
				info = SearchById(info);
			}
		}
		return info;
	}

	// 修改
	@SuppressWarnings("unchecked")
	public int Update(String id, JSONObject object) {
		String message = SearchById(id);
		String tip = JSONHelper.string2json(message).get("message").toString();
		JSONObject records = (JSONObject) JSONHelper.string2json(tip).get("records");
		if (records.containsKey("Rgroup")) {
			if ((" ").equals(records.get("Rgroup").toString())) {
				bind().eq("Rgroup", records.get("Rgroup").toString()).data(object).updateAll();
			}
		}
		if (object.containsKey("reason")) {
			String content = codec.decodebase64(object.get("reason").toString());
			if (content.contains("@t")) {
				content.replace("@t", "/");
			}
			if (content.contains("@w")) {
				content.replace("@w", "+");
			}
			object.put("reason", content);
		}
		int code = bind().eq("_id", new ObjectId(id)).data(object).update() != null ? 0 : 99;
		return code;
	}

	// 删除
	public int Delete(String id) {
		int code = bind().eq("_id", new ObjectId(id)).delete() != null ? 0 : 99;
		return code;
	}

	// 删除被拒绝的举报件
	public int Delete() {
		String code = String.valueOf(bind().eq("state", 3).deleteAll());
		return Integer.parseInt(code);
	}

	// 批量删除
	public int Delete(String[] ids) {
		int len = ids.length;
		bind().or();
		for (int i = 0; i < len; i++) {
			bind().eq("_id", new ObjectId(ids[i]));
		}
		int code = bind().deleteAll() == len ? 0 : 99;
		return code;
	}

	// 分页
	@SuppressWarnings("unchecked")
	public String page(int ids, int pageSize) {
		JSONArray array = bind().desc("time").page(ids, pageSize);
		if (array.size() == 0) {
			return resultMessage(0, "");
		}
		JSONObject object = new JSONObject();
		JSONArray array2 = dencode(array); // 获取举报信息图片或者视频等
		object.put("totalSize", (int) Math.ceil((double) bind().count() / pageSize));
		object.put("pageSize", pageSize);
		object.put("currentPage", ids);
		object.put("data", getImg(array2));
		return resultMessage(object);
	}

	@SuppressWarnings("unchecked")
	private JSONArray dencode(JSONArray array) {
		if (array.size() == 0) {
			return array;
		}
		JSONArray arry = new JSONArray();
		for (int i = 0; i < array.size(); i++) {
			JSONObject object = (JSONObject) array.get(i);
			if (object.containsKey("content") && object.get("content") != "") {
				object.put("content", codec.decodebase64(object.get("content").toString()));
			}
			arry.add(object);
		}
		return arry;
	}

	@SuppressWarnings("unchecked")
	private JSONObject dencode(JSONObject obj) {
		obj.put("content", codec.decodebase64(obj.get("content").toString()));
		return obj;
	}

	// 条件分页
	@SuppressWarnings("unchecked")
	public String page(int ids, int pageSize, JSONObject objects) {
		bind().and();
		for (Object obj : objects.keySet()) {
			if (obj.equals("_id")) {
				bind().eq("_id", new ObjectId(objects.get("_id").toString()));
			}
			bind().eq(obj.toString(), objects.get(obj.toString()));

		}
		JSONArray array = bind().dirty().desc("time").page(ids, pageSize);
		JSONArray array2 = dencode(array);
		JSONObject object = new JSONObject();
		object.put("totalSize", (int) Math.ceil((double) bind().count() / pageSize));
		object.put("pageSize", pageSize);
		object.put("currentPage", ids);
		object.put("data", getImg(array2));
		return resultMessage(object);
	}

	// 模糊查询
	@SuppressWarnings("unchecked")
	public String find(int ids, int pageSize, JSONObject objects) {
		bind().or();
		for (Object obj : objects.keySet()) {
			bind().like(obj.toString(), objects.get(obj.toString()));
		}
		JSONArray array = bind().dirty().page(ids, pageSize);
		JSONArray array2 = dencode(array);
		JSONObject object = new JSONObject();
		object.put("totalSize", (int) Math.ceil((double) bind().count() / pageSize));
		object.put("pageSize", pageSize);
		object.put("currentPage", ids);
		object.put("data", getImg(array2));
		return resultMessage(object);
	}

	// 批量查询
	public String Select(JSONObject object, int no) {
		bind().and();
		for (Object obj : object.keySet()) {
			if (obj.equals("_id")) {
				bind().eq("_id", new ObjectId(object.get("_id").toString()));
			}
			String value = object.get(obj.toString()).toString();
			if (value.contains(",")) {
				getCond(report, obj.toString(), value.split(","));
			} else {
				bind().eq(obj.toString(), object.get(obj.toString()).toString());
			}
		}
		JSONArray array = bind().limit(no).select();
		if (array.size() == 0) {
			resultMessage(0, "");
		}
		JSONArray array2 = dencode(array);
		return resultMessage(getImg(array2));
	}

	private DBHelper getCond(DBHelper rep, String key, String[] values) {
		rep.or();
		for (int i = 0; i < values.length; i++) {
			rep.eq(key, Long.parseLong(values[i]));
		}
		return rep;
	}

	public String finds(JSONObject object) {
		if (object == null) {
			bind().eq("state", 1L);
		} else {
			bind().and();
			for (Object obj : object.keySet()) {
				if (obj.equals("_id")) {
					bind().eq("_id", new ObjectId(object.get("_id").toString()));
				}
				if (obj.equals("state")) {
					bind().eq(obj.toString(), Long.parseLong(object.get(obj.toString()).toString()));
				} else {
					bind().like(obj.toString(), object.get(obj.toString()).toString());
				}
			}
		}
		JSONArray array = bind().limit(50).select();
		return resultMessage(getImg(dencode(array)));
	}

	// 举报件处理完成
	@SuppressWarnings("unchecked")
	public int Complete(String id, JSONObject reasons) {
		int code = 0;
		if (!reasons.containsKey("state")) {
			reasons.put("state", 2);
		}
		if (!reasons.containsKey("completetime")) {
			reasons.put("completetime", String.valueOf(TimeHelper.nowMillis()));
		}
		if (reasons.containsKey("reason")) {
			String content = codec.decodebase64(reasons.get("reason").toString());
			if (content.contains("@t")) {
				content.replace("@t", "/");
			}
			if (content.contains("@w")) {
				content.replace("@w", "+");
			}
			reasons.put("reason", content);
		}
		// appsProxy
		// .proxyCall(host,
		// appid + "/45/Reason/addTime/s:"
		// + reasons.get("reason").toString(),
		// null, "")
		// .toString();
		String message = SearchById(id);
		String tip = JSONHelper.string2json(message).get("message").toString();
		JSONObject records = (JSONObject) JSONHelper.string2json(tip).get("records");
		if (records.containsKey("Rgroup")) {
			if (!("").equals(records.get("Rgroup").toString())) {
				bind().eq("Rgroup", records.get("Rgroup").toString()).data(reasons).updateAll();
			}
		} else {
			code = bind().eq("_id", new ObjectId(id)).data(reasons).update() != null ? 0 : 99;
		}
		// if (code == 0) {
		// 发送短信只举报人
		// SendSMS(id);
		// 发送微信至举报人
		// }
		// 添加操作日志
		JSONObject object = new JSONObject();
		// object.put("OperateId", reasons.get("").toString());
		object.put("ReportId", id);
		object.put("time", TimeHelper.nowMillis());
		object.put("ContentLog", reasons.get("reason").toString());
		object.put("step", "该举报件已处理完结");
		appsProxy.proxyCall(host, appid + "/45/ReportLog/Addlog/" + object.toString(), null, "");
		return code;
	}

	// 举报拒绝
	@SuppressWarnings("unchecked")
	public int Refuse(String id, JSONObject reasons) {
		int code = 0;
		if (!reasons.containsKey("state")) {
			reasons.put("state", 3);
		}
		if (!reasons.containsKey("refusetime")) {
			reasons.put("refusetime", String.valueOf(TimeHelper.nowMillis()));
		}
		if (!reasons.containsKey("isdelete")) {
			reasons.put("isdelete", 1);
		}
		if (reasons.containsKey("reason")) {
			String content = codec.decodebase64(reasons.get("reason").toString());
			if (content.contains("@t")) {
				content.replace("@t", "/");
			}
			if (content.contains("@w")) {
				content.replace("@w", "+");
			}
			reasons.put("reason", content);
		}
		if (!reasons.get("reason").toString().contains("/")) {
			appsProxy.proxyCall(host, appid + "/45/Reason/addTime/s:" + reasons.get("reason").toString(), null, "")
					.toString();
		}
		String message = SearchById(id);
		String tip = JSONHelper.string2json(message).get("message").toString();
		if (!("").equals(tip)) {
			JSONObject records = (JSONObject) JSONHelper.string2json(tip).get("records");
			if (records.containsKey("Rgroup")) {
				if (!("").equals(records.get("Rgroup").toString())) {
					bind().eq("Rgroup", records.get("Rgroup").toString()).data(reasons).updateAll();
				}
			} else {
				code = bind().eq("_id", new ObjectId(id)).data(reasons).update() != null ? 0 : 99;
			}
		}
		// 添加操作日志
		JSONObject object = new JSONObject();
		// object.put("OperateId", reasons.get("").toString());
		object.put("ReportId", id);
		object.put("time", TimeHelper.nowMillis());
		object.put("ContentLog", reasons.get("reason").toString());
		object.put("step", "该举报件已处理完结");
		appsProxy.proxyCall(host, appid + "/45/ReportLog/Addlog/" + object.toString(), null, "");
		return code;
	}

	// 发送处理件短信至举报人
	private void SendSMS(String id) {
		String message = SearchById(id);
		String msg = JSONHelper.string2json(message).get("message").toString();
		JSONObject object = JSONHelper.string2json(msg);
		if (object.containsKey("InformantPhone")) {
			String phone = object.get("InformantPhone").toString();
			String text = "";
			if ("3".equals(object.get("state").toString())) {
				text = "您举报的" + object.get("content").toString() + "已被拒绝," + "拒绝理由为：" + object.get("reason").toString();
			}
			if ("2".equals(object.get("state").toString())) {
				text = "您举报的" + object.get("content").toString() + "已处理完成";
			}
			ruoyaMASDB.sendSMS(phone, text);
		}
	}

	@SuppressWarnings("unchecked")
	public int SendVerity(String phone, String text) {
		session session = new session();
		String time = "";
		String currenttime = TimeHelper.stampToDate(TimeHelper.nowMillis()).split(" ")[0];
		int count = 0;
		JSONObject object = new JSONObject();
		if (session.get(phone) != null) {
			object = JSONHelper.string2json(session.get(phone).toString());
			count = Integer.parseInt(object.get("count").toString()); // 次数
			time = TimeHelper.stampToDate(Long.parseLong(object.get("time").toString()));
			time = time.split(" ")[0];
			if (currenttime.equals(time) && count == 5) {
				return 11;
			}
		}
		String tip = ruoyaMASDB.sendSMS(phone, text);
		count++;
		object.put("count", count + "");
		object.put("time", TimeHelper.nowMillis());
		session.setget(phone, object);
		return tip != null ? 0 : 99;
	}

	// 查询个人相关的举报件
	public String search(String userid, int no) {
		JSONArray array = bind().and().eq("userid", userid).ne("state", 0).limit(no).select();
		JSONArray array2 = getImg(array);
		return resultMessage(array2);
	}

	public String counts(String userid) {
		long count = bind().eq("userid", userid).count();
		return resultMessage(0, String.valueOf(count));
		// return null;
	}

	public String counts(JSONObject object) {
		long count = bind().eq("state", Integer.parseInt(object.get("state").toString())).count();
		return resultMessage(0, String.valueOf(count));
		// return null;
	}

	public String feed(String userid) {
		long count = bind().eq("userid", userid).ne("state", 0).count();
		return resultMessage(0, String.valueOf(count));
	}

	// 导出举报件
	public String print(String info) {
		String Date = TimeHelper.stampToDate(TimeHelper.nowMillis()).split(" ")[0];
		File file = excelHelper.out("45/Report/find/" + info);
		if (file == null) {
			return resultMessage(0, "没有符合条件的数据");
		}
		String uuid = UUID.randomUUID().toString();
		File tarFile = new File("C:\\JavaCode\\tomcat\\webapps\\File\\upload\\" + Date + "\\Grape" + uuid + ".xls");
		file.renameTo(tarFile);
		String target = tarFile.toString();
		target = "http://123.57.214.226:8080" + target.split("webapps")[1];
		return resultMessage(0, target);
	}

	// 显示举报信息，包含上一篇，下一篇
	@SuppressWarnings("unchecked")
	public String SearchById(String id) {
		JSONObject object = bind().eq("_id", new ObjectId(id)).find();
		if (object == null) {
			return resultMessage(0, "");
		}
		JSONObject preobj = find(object.get("time").toString(), "<");
		JSONObject nextobj = find(object.get("time").toString(), ">");
		object.put("previd", getpnReport(preobj).get("id"));
		object.put("prevname", getpnReport(preobj).get("name"));
		object.put("nextid", getpnReport(nextobj).get("id"));
		object.put("nextname", getpnReport(nextobj).get("name"));
		return resultMessage(getImg(dencode(object)));
	}

	public String insert(JSONObject info) {
		return bind().data(info).insertOnce().toString();
	}

	// 实名举报
	public String RealName(JSONObject object) {
		String openid = object.get("userid").toString();
		// 判断是否实名认证
		String message = appsProxy.proxyCall(host, appid + "/16/wechatUser/FindOpenId/" + openid, null, "").toString();
		String tip = JSONHelper.string2json(message).get("message").toString();
		if (!("").equals(tip)) {
			String phone = JSONHelper.string2json(tip).get("phone").toString();
			String ckcode = getValidateCode();
			// 1.发送验证码
			int code = SendVerity(phone, "验证码:" + ckcode);
			if (code == 0) {
				String nextstep = appid + "/45/Report/insert/" + object.toString();
				boolean flag = interrupt._break(ckcode, phone, nextstep, appid + "");
				code = flag ? 0 : 99;
			}
			return resultMessage(code, "验证码发送成功");
		}
		return resultMessage(99);
	}

	// 恢复当前操作
	@SuppressWarnings("unchecked")
	public String resu(JSONObject object) {
		String message = "";
		session session = new session();
		String openid = object.get("openid").toString();
		// 判断是否实名认证
		String messages = appsProxy.proxyCall(host, appid + "/16/wechatUser/FindOpenId/" + openid, null, "").toString();
		if (!("").equals(messages)) {
			String tips = JSONHelper.string2json(messages).get("message").toString();
			if (!("").equals(tips)) {
				return resultMessage(14);
			}
			if (!object.containsKey("phone")) {
				String phone = JSONHelper.string2json(tips).get("phone").toString();
				object.put("phone", phone);
			}
		}
		// if (object == null) {
		// return resultMessage(10);
		// }
		if (object.containsKey("ckcode") && object.containsKey("phone")) {
			int code = interrupt._resume(object.get("ckcode").toString(), object.get("phone").toString(),
					String.valueOf(appsProxy.appid()));
			if (code == 0) {
				return resultMessage(4);
			}
			if (code == 1) {
				return resultMessage(5);
			}
			if (("2").equals(object.get("type").toString())) {
				String url = appid + "/45/Report/insert/s:" + session.get(object.get("openid").toString());
				String tip = appsProxy.proxyCall(host, url, null, "").toString();
				// String message = appsProxy.proxyCall(host,
				// "/15/wechatUser/FindById/"
				// + object.get("openid").toString(),
				// null, "").toString();
				// return message;
			}
			message = appsProxy.proxyCall(host, appid + "/16/wechatUser/FindOpenId/" + openid, null, "").toString();
		}
		nlogger.logout(" resume message" + message);
		return message;
	}

	// 验证内容是否含有敏感字符串
	public int checkCont(String content) {
		if (WordFilter.isContains(content)) {
			return 3;
		}
		return 0;
	}

	// 获取用户openid，实名认证
	@SuppressWarnings("unchecked")
	public String getId(String code, String url) {
		// 获取微信签名
		String signMsg = appsProxy.proxyCall(host, appid + "/30/Wechat/getSignature/" + url, null, "").toString();
		String sign = JSONHelper.string2json(signMsg).get("message").toString();
		JSONObject object = new JSONObject();
		String openid = appsProxy.proxyCall(host, appid + "/30/Wechat/BindWechat/" + code, null, "").toString();
		if (openid.equals("")) {
			return jGrapeFW_Message.netMSG(7, "code错误");
		}
		// 将获取到的openid与库表中的openid进行比对，若存在已绑定，否则未绑定
		String message = appsProxy.proxyCall(host, appid + "/16/wechatUser/FindOpenId/" + openid, null, "").toString();
		nlogger.logout("message:" + message);
		String tip = JSONHelper.string2json(message).get("message").toString();
		if (!("").equals(tip)) {
			object.put("msg", "已实名认证");
			object.put("openid", openid);
			object.put("headimgurl", JSONHelper.string2json(tip).get("headimgurl").toString());
			object.put("sign", sign);
			return jGrapeFW_Message.netMSG(0, object.toString());
		}
		object.put("msg", "未实名认证");
		object.put("openid", openid);
		object.put("sign", sign);
		return jGrapeFW_Message.netMSG(1, object.toString());
	}

	// 实名认证
	public int Certify(String info) {
		// System.out.println(info);
		JSONObject object = JSONHelper.string2json(info);
		if (object == null) {
			return 10;
		}
		if (!object.containsKey("openid")) {
			return 8;
		}
		String phone = object.get("phone").toString();
		if (!checkPhone(phone)) {
			return 5;
		}
		// 发送短信验证码,中断当前操作
		// 获取随机6位验证码
		String ckcode = getValidateCode();
		// 1.发送验证码
		// String text = "您的验证码为：" + ckcode;

		int code = SendVerity(object.get("phone").toString(), "验证码为：" + ckcode);
		if (code == 0) {
			String nextstep = appid + "/16/wechatUser/insertOpenId/" + info;
			// 2.中断[参数：随机验证码，手机号，下一步操作，appid]
			boolean flag = interrupt._break(ckcode, phone, nextstep, appid + "");
			code = flag ? 0 : 99;
			// if (("2").equals(object.get("type").toString())) {
			// if (code == 0) {
			// info = session.get(object.get("openid").toString())
			// .toString();
			// System.out.println(info.getBytes().length);
			// nlogger.logout(info);
			//// String step = info.replace("\"", "\\\"");
			// interrupt._break(ckcode, object.get("phone").toString(),
			// appid + "/45/Report/insert/s:" + info, appid + "");
			// code = flag ? 0 : 99;
			// }
			// }
		}
		return code;
	}

	// 用户封号
	@SuppressWarnings("unchecked")
	public String UserKick(String openid, JSONObject object) {
		if (!object.containsKey("isdelete")) {
			object.put("isdelete", "1");
		}
		if (!object.containsKey("time")) {
			object.put("time", TimeHelper.nowMillis() + "");
		}
		return appsProxy
				.proxyCall(host, appid + "/16/wechatUser/KickUser/" + openid + "/" + object.toString(), null, "")
				.toString();
	}

	// 解封
	public String UserUnKick() {
		return appsProxy.proxyCall(host, appid + "/16/wechatUser/unkick/", null, "").toString();
	}

	// 举报量统计
	public String EventCounts(JSONObject object) {
		db db = setCondString(bind().and(), object);
		return resultMessage(0, String.valueOf(db.count()));
	}

	// 举报量与全量比例
	public String PercentCounts(JSONObject object) {
		float count = (float) bind().count();
		db db = setCondString(bind().and(), object);
		float Eventcount = (float) db.count();
		return resultMessage(0, String.format("%.2f", (double) (Eventcount / count)));
	}

	// 定时发送新增举报量到管理员手机号
	public String TimerSend(JSONObject object) {

		long count = bind().count();
		db db = setCondString(bind().and(), object);
		long Eventcount = db.count();
		return resultMessage(0, String.format("%.2f", Eventcount / count));
	}

	// 统计24小时内新增举报量
	@SuppressWarnings("unchecked")
	public String TimerInsertCount(JSONObject threadInfo) {
		long InsertCount = 0;
		DBHelper tmpdb = getdb(threadInfo);
		if (tmpdb != null) {
			long currentTime = TimeHelper.nowMillis();
			String OpTime = String.valueOf(currentTime - 24 * 3600 * 1000);
			JSONObject object = new JSONObject();
			object.put("time", OpTime + "~" + String.valueOf(currentTime));
			JSONObject objects = getTime(object.get("time").toString());
			InsertCount = tmpdb.gte("time", objects.get("start").toString()).lte("time", objects.get("end").toString())
					.count();
		}
		return String.valueOf(InsertCount);
	}

	@SuppressWarnings("unchecked")
	public int ReportJoin(String[] ids) {
		List<String> list = new ArrayList<>();
		JSONObject object = new JSONObject();
		bind().or();
		for (int i = 0; i < ids.length; i++) {
			String message = SearchById(ids[i]);
			String tip = JSONHelper.string2json(message).get("message").toString();
			JSONObject records = (JSONObject) JSONHelper.string2json(tip).get("records");
			if (records.containsKey("Rgroup")) {
				if (!(" ").equals(records.get("Rgroup").toString())) {
					return 17;
				}
			}
			bind().eq("_id", new ObjectId(ids[i]));
			if (!judgeState(ids[i])) {
				return 16;
			}
			list.add(ids[i]);
		}
		object.put("content", StringHelper.join(list));
		// 新增到事件组，获取组id
		String message = appsProxy.proxyCall(host, appid + "/45/ReportGroup/AddRgroup" + object.toString(), null, "")
				.toString();
		String Rgroup = JSONHelper.string2json(message).get("message").toString();
		if (!("").equals(Rgroup)) {
			JSONObject objects = new JSONObject();
			objects.put("Rgroup", Rgroup);
			bind().data(objects).updateAll();
			return 0;
		}
		return 99;
	}

	private boolean judgeState(String id) {
		session session = new session();
		String message = SearchById(id);
		String tip = JSONHelper.string2json(message).get("message").toString();
		JSONObject records = (JSONObject) JSONHelper.string2json(tip).get("records");
		String state = records.get("state").toString();
		if (session.get("state") != null) {
			if (!session.get("state").toString().equals(state)) {
				return false;
			}
		}
		session.setget("state", state);
		return true;
	}

	// 统计量与统计量所有量比例条件设置
	private db setCondString(db db, JSONObject object) {
		for (Object obj : object.keySet()) {
			if ("time".equals(obj.toString()) || "handletime".equals(obj.toString())
					|| "completetime".equals(obj.toString()) || "refusetime".equals(obj.toString())) {
				JSONObject objects = getTime(object.get(obj.toString()).toString());
				db = bind().gte(obj.toString(), objects.get("start").toString()).lte(obj.toString(),
						objects.get("start").toString());
			} else {
				db = bind().eq(obj.toString(), object.get(obj.toString()));
			}
		}
		return db;
	}

	// 获取时间区间开始时间和结束时间
	@SuppressWarnings("unchecked")
	private JSONObject getTime(String times) {
		JSONObject object = new JSONObject();
		long starts = 0, ends = 0;
		String start = times.split("~")[0];
		String end = times.split("~")[1];
		if (start.contains(" ")) {
			try {
				starts = TimeHelper.dateToStamp(start);
				ends = TimeHelper.dateToStamp(start);
			} catch (ParseException e) {
				e.printStackTrace();
			}
		} else {
			starts = Long.parseLong(start);
			ends = Long.parseLong(end);
		}
		object.put("start", starts);
		object.put("end", ends);
		return object;
	}

	// 获取举报信息[上一条，下一条显示]
	@SuppressWarnings("unchecked")
	private JSONObject getpnReport(JSONObject object) {
		String id = null;
		String name = null;
		JSONObject object2 = new JSONObject();
		if (object != null) {
			JSONObject obj = (JSONObject) object.get("_id");
			id = obj.get("$oid").toString();
			name = object.get("content").toString();
			object2.put("id", id);
			object2.put("name", name);
		}
		return object2;
	}

	private JSONObject find(String time, String logic) {
		if (time.contains("$numberLong")) {
			JSONObject object = JSONHelper.string2json(time);
			time = object.get("$numberLong").toString();
		}
		if (logic == "<") {
			bind().lt("time", time).desc("time");
		} else {
			bind().gt("time", time).asc("time");
		}
		return bind().find();
	}

	@SuppressWarnings("unchecked")
	private JSONArray getImg(JSONArray array) {
		if (array.size() == 0) {
			return array;
		}
		JSONArray array2 = new JSONArray();
		for (int i = 0; i < array.size(); i++) {
			JSONObject object = (JSONObject) array.get(i);
			array2.add(getImg(object));
		}
		return array2;
	}

	@SuppressWarnings("unchecked")
	private JSONObject getImg(JSONObject object) {
		if (object == null) {
			return null;
		}
		List<String> liStrings = new ArrayList<String>();
		if (object.containsKey("attr1")) {
			object = getFile("attrFile1", object.get("attr1").toString(), object);
		}
		if (object.containsKey("attr2")) {
			object = getFile("attrFile2", object.get("attr2").toString(), object);
		}
		if (object.containsKey("attr3")) {
			object = getFile("attrFile3", object.get("attr3").toString(), object);
		}
		if (object.containsKey("attr4")) {
			object = getFile("attrFile4", object.get("attr4").toString(), object);
		}
		// if (object.containsKey("mediaid")) {
		// liStrings = getMedia(liStrings, object.get("mediaid").toString());
		// }
		// 获取举报类型
		if (object.containsKey("type")) {
			object = getType(object);
		}
		if (imgList.size() != 0) {
			object.put("image", StringHelper.join(imgList));
		} else {
			object.put("image", "");
		}
		if (videoList.size() != 0) {
			object.put("video", StringHelper.join(videoList));
		} else {
			object.put("video", "");
		}
		// if (liStrings.size() != 0) {
		// object.put("media", StringHelper.join(liStrings));
		// } else {
		// object.put("media", "");
		// }
		return object;
	}

	private List<String> getMedia(List<String> list, String mediaid) {
		if (("").equals(mediaid)) {
			return list;
		}
		String message = appsProxy.proxyCall(host, appid + "/30/Wechat/downloadMedia/" + mediaid, null, "").toString();
		nlogger.logout(message);
		String url = JSONHelper.string2json(message).get("message").toString();
		url = "http://123.57.214.226:8080" + url;
		if (!("").equals(url)) {
			list.add(url);
		}
		return list;
	}

	@SuppressWarnings("unchecked")
	private JSONObject getType(JSONObject object) {
		if ("0".equals(object.get("type").toString())) {
			object.put("ReportType", "");
		} else {
			String message = appsProxy.proxyCall(host,
					appsProxy.appid() + "/45/Rtype/findById/" + object.get("type").toString(), null, "").toString();
			String msg = JSONHelper.string2json(message).get("message").toString();
			if (("").equals(msg)) {
				object.put("ReportType", "");
			} else {
				msg = JSONHelper.string2json(msg).get("records").toString();
				object.put("ReportType", JSONHelper.string2json(msg).get("TypeName").toString());
			}
		}
		return object;
	}

	private List<String> getImgUrl(List<String> list, String imgId) {
		if (("").equals(imgId)) {
			return list;
		}
		String url = appsProxy.proxyCall(host, appid + "/24/Files/geturl/" + imgId, null, "").toString();
		if (!("").equals(url)) {
			list.add(url);
		}
		return list;
	}

	@SuppressWarnings("unchecked")
	private JSONObject getFile(String key, String imgid, JSONObject object) {
		session session = new session();
		if (!("").equals(imgid)) {
			String fileInfo = "";
			if (session.get(imgid) != null) {
				fileInfo = session.get(imgid).toString();
			} else {
				// 获取文件对象
				fileInfo = appsProxy.proxyCall(host, appid + "/24/Files/getFile/" + imgid, null, "").toString();
				fileInfo = JSONHelper.string2json(fileInfo).get("message").toString();
			}
			if (("").equals(fileInfo)) {
				object.put(key, "");
			} else {
				JSONObject object2 = JSONHelper.string2json(fileInfo);
				object.put(key, object2);
				if ("1".equals(object2.get("filetype").toString())) {
					imgList.add("http://123.57.214.226:8080" + object2.get("filepath").toString());
				}
				if ("2".equals(object2.get("filetype").toString())) {
					videoList.add("http://123.57.214.226:8080" + object2.get("filepath").toString());
				}
			}
		}
		return object;
	}

	private boolean checkPhone(String mob) {
		return checkHelper.checkMobileNumber(mob);
	}

	// private String getRecord(JSONArray array){
	//
	// }
	// 设置验证项
	private formHelper setCheck() {
		// form.putRule("Wrongdoer", formdef.notNull);
		form.putRule("WrongdoerSex", formdef.notNull);
		form.putRule("mode", formdef.notNull);
		form.putRule("type", formdef.notNull);
		form.putRule("content", formdef.notNull);
		form.putRule("region", formdef.notNull); // 地区
		// form.putRule("workplace", formdef.notNull);
		// form.putRule("post", formdef.notNull);
		return form;
	}

	private String getValidateCode() {
		String num = "";
		for (int i = 0; i < 6; i++) {
			num = num + String.valueOf((int) Math.floor(Math.random() * 9 + 1));
		}
		return num;
	}

	/**
	 * 将map添加至JSONObject中
	 * 
	 * @param map
	 * @param object
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public JSONObject AddMap(HashMap<String, Object> map, JSONObject object) {
		if (map.entrySet() != null) {
			Iterator<Entry<String, Object>> iterator = map.entrySet().iterator();
			while (iterator.hasNext()) {
				Map.Entry<String, Object> entry = (Map.Entry<String, Object>) iterator.next();
				if (!object.containsKey(entry.getKey())) {
					object.put(entry.getKey(), entry.getValue());
				}
			}
		}
		return object;
	}

	@SuppressWarnings("unchecked")
	private String resultMessage(JSONObject object) {
		_obj.put("records", object);
		return resultMessage(0, _obj.toString());
	}

	@SuppressWarnings("unchecked")
	private String resultMessage(JSONArray array) {
		_obj.put("records", array);
		return resultMessage(0, _obj.toString());
	}

	private String resultMessage(int num) {
		return resultMessage(num, "");
	}

	public String resultMessage(int num, String message) {
		String msg = "";
		switch (num) {
		case 0:
			msg = message;
			break;
		case 1:
			msg = "必填项为空";
			break;
		case 2:
			msg = "手机号格式错误";
			break;
		case 3:
			msg = "存在敏感字符串";
			break;
		case 4:
			msg = "下一步操作不存在";
			break;
		case 5:
			msg = "验证码错误";
			break;
		case 6:
			msg = "内容超过指定字数";
			break;
		case 8:
			msg = "信息不完整";
			break;
		case 9:
			msg = "没有权限";
			break;
		case 10:
			msg = "参数格式错误";
			break;
		case 11:
			msg = "您今日短信发送次数已达上线";
			break;
		case 12:
			msg = "您还未进行实名绑定";
			break;
		case 13:
			msg = "身份证号格式错误";
			break;
		case 14:
			msg = "您已实名认证过";
			break;
		case 15:
			msg = "无法获取openid";
			break;
		case 16:
			msg = "不同类型的举报件不能进行合并";
			break;
		case 17:
			msg = "该事件已被合并至其他事件";
			break;
		case 18:
			msg = "用户不存在";
			break;
		default:
			msg = "其他操作异常";
			break;
		}
		return jGrapeFW_Message.netMSG(num, msg);
	}
}
