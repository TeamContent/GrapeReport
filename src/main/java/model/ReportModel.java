package model;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.bson.types.ObjectId;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import apps.appsProxy;
import database.db;
import esayhelper.DBHelper;
import esayhelper.JSONHelper;
import esayhelper.StringHelper;
import esayhelper.TimeHelper;
import esayhelper.checkHelper;
import esayhelper.formHelper;
import esayhelper.jGrapeFW_Message;
import esayhelper.formHelper.formdef;
import filterword.WordFilter;
import interrupt.interrupt;
import nlogger.nlogger;
import offices.excelHelper;
import session.session;
import sms.ruoyaMASDB;

public class ReportModel {
	private static DBHelper report;
	private static formHelper form;
	private JSONObject _obj = new JSONObject();
	private int count = 0;
	private static int appid = appsProxy.appid();
	private String host = "123.57.214.226:801";
	// private wechatHelper helper = new wechatHelper("wx604cf5644f5c216b",
	// "f93d37ac273c3b313850848d771e5e27");

	static {
		report = new DBHelper(appsProxy.configValue().get("db").toString(),
				"reportInfo");
		form = report.getChecker();
	}

	private db bind() {
		return report.bind(appsProxy.appid()+"");
	}

	// 新增
	public String Add(JSONObject object) {
		// 判断用户是否被封号
		String message = appsProxy
				.proxyCall(host,
						appid + "/16/wechatUser/FindOpenId/"
								+ object.get("userid").toString(),
						null, "")
				.toString();
		String msg = JSONHelper.string2json(message).get("message").toString();
		if (("1").equals(
				JSONHelper.string2json(msg).get("isdelete").toString())) {
			return resultMessage(9);
		}
		if (!(object.get("content").toString().length() <= 500)) {
			return resultMessage(6);
		}
		int mode = Integer.parseInt(object.get("mode").toString());
		String info = "";
		if (mode == 0) {
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
	public int Update(String id, JSONObject object) {
		int code = bind().eq("_id", new ObjectId(id)).data(object)
				.update() != null ? 0 : 99;
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
		JSONArray array = bind().page(ids, pageSize);
		JSONObject object = new JSONObject();
		object.put("totalSize",
				(int) Math.ceil((double) bind().count() / pageSize));
		object.put("pageSize", pageSize);
		object.put("currentPage", ids);
		object.put("data", getImg(array));
		return resultMessage(object);
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
		JSONArray array = bind().dirty().page(ids, pageSize);
		JSONObject object = new JSONObject();
		object.put("totalSize",
				(int) Math.ceil((double) bind().count() / pageSize));
		object.put("pageSize", pageSize);
		object.put("currentPage", ids);
		object.put("data", getImg(array));
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
		JSONObject object = new JSONObject();
		object.put("totalSize",
				(int) Math.ceil((double) bind().count() / pageSize));
		object.put("pageSize", pageSize);
		object.put("currentPage", ids);
		object.put("data", getImg(array));
		return resultMessage(object);
	}

	// 批量查询
	public String Select(JSONObject object, int no) {
		bind().or();
		for (Object obj : object.keySet()) {
			if (obj.equals("_id")) {
				bind().eq("_id", new ObjectId(object.get("_id").toString()));
			}
			String value = object.get(obj.toString()).toString();
			if (value.contains(",")) {
				getCond(report, obj.toString(), value.split(","));
			} else {
				bind().eq(obj.toString(),
						object.get(obj.toString()).toString());
			}

		}
		return resultMessage(getImg(bind().limit(no).select()));
	}

	private DBHelper getCond(DBHelper rep, String key, String[] values) {
		for (int i = 0; i < values.length; i++) {
			rep.eq(key, Long.parseLong(values[i]));
		}
		return rep;
	}

	public String finds(JSONObject object) {
		if (object == null) {
			bind().eq("state", 1L);
		} else {
			for (Object obj : object.keySet()) {
				if (obj.equals("_id")) {
					bind().eq("_id",
							new ObjectId(object.get("_id").toString()));
				}
				bind().eq(obj.toString(),
						object.get(obj.toString()).toString());
			}
		}
		return resultMessage(getImg(bind().limit(50).select()));
	}

	// 举报件处理完成
	@SuppressWarnings("unchecked")
	public int Complete(String id, JSONObject reasons) {
		if (!reasons.containsKey("state")) {
			reasons.put("state", 2);
		}
		if (!reasons.containsKey("completetime")) {
			reasons.put("completetime", String.valueOf(TimeHelper.nowMillis()));
		}
		appsProxy
				.proxyCall(host,
						appid + "/45/Reason/addTime/s:"
								+ reasons.get("reason").toString(),
						null, "")
				.toString();
		int code = bind().eq("_id", new ObjectId(id)).data(reasons)
				.update() != null ? 0 : 99;
		if (code == 0) {
			// 发送短信只举报人
			SendSMS(id);
			// 发送微信至举报人

		}
		return code;
	}

	// 举报拒绝
	@SuppressWarnings("unchecked")
	public int Refuse(String id, JSONObject reasons) {
		if (!reasons.containsKey("state")) {
			reasons.put("state", 3);
		}
		if (!reasons.containsKey("refusetime")) {
			reasons.put("refusetime", String.valueOf(TimeHelper.nowMillis()));
		}
		if (!reasons.containsKey("isdelete")) {
			reasons.put("isdelete", 1);
		}
		appsProxy
				.proxyCall(host,
						appid + "/45/Reason/addTime/s:"
								+ reasons.get("reason").toString(),
						null, "")
				.toString();
		int code = bind().eq("_id", new ObjectId(id)).data(reasons)
				.update() != null ? 0 : 99;
		if (code == 0) {
			// 发送短信只举报人
			SendSMS(id);
			// 发送微信至举报人
		}
		return code;
	}

	// 微信回复举报人
	// public String replyByWechat(String userid, JSONObject content) {
	// JSONArray array = new JSONArray();
	// JSONObject object = new JSONObject();
	// object.put("openid", userid);
	// array.add(object);
	// JSONObject obj = helper.send2all(array, content,
	// wechatModel.MSGTYPE_TEXT);
	// return obj != null ? resultMessage(0) : resultMessage(99);
	// }

	// 发送处理件短信至举报人
	private void SendSMS(String id) {
		String message = SearchById(id);
		String msg = JSONHelper.string2json(message).get("message").toString();
		JSONObject object = JSONHelper.string2json(msg);
		if (object.containsKey("InformantPhone")) {
			String phone = object.get("InformantPhone").toString();
			String text = "";
			if ("3".equals(object.get("state").toString())) {
				text = "您举报的" + object.get("content").toString() + "已被拒绝,"
						+ "拒绝理由为：" + object.get("reason").toString();
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
		String currenttime = TimeHelper.stampToDate(TimeHelper.nowMillis())
				.split(" ")[0];
		int count = 0;
		JSONObject object = new JSONObject();
		if (session.get(phone) != null) {
			System.out.println(session.get(phone).toString());
			object = JSONHelper.string2json(session.get(phone).toString());
			count = Integer.parseInt(object.get("count").toString()); // 次数
			time = TimeHelper
					.stampToDate(Long.parseLong(object.get("time").toString()));
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
		JSONArray array = bind().and().eq("userid", userid).ne("state", 0)
				.limit(no).select();
		return resultMessage(getImg(array));
	}

	public String counts(String userid) {
		long count = bind().eq("userid", userid).count();
		return resultMessage(0, String.valueOf(count));
	}

	public String feed(String userid) {
		long count = bind().eq("userid", userid).ne("state", 0).count();
		return resultMessage(0, String.valueOf(count));
	}

	// 查询含有反馈信息的举报件
	// public String Show(String userid, int no) {
	// JSONArray array = bind().and().eq("userid", userid).ne("state", 0)
	// .limit(no).select();
	// return resultMessage(getImg(array));
	// }

	// 导出举报件
	public String print(String info) {
		File file = excelHelper.out("45/Report/find/" + info);
		if (file == null) {
			return resultMessage(0, "没有符合条件的数据");
		}
		return resultMessage(0, file.toString());
	}

	// 显示举报信息，包含上一篇，下一篇
	@SuppressWarnings("unchecked")
	public String SearchById(String id) {
		JSONObject object = bind().eq("_id", new ObjectId(id)).find();
		JSONObject preobj = find(object.get("time").toString(), "<");
		JSONObject nextobj = find(object.get("time").toString(), ">");
		object.put("previd", getpnReport(preobj).get("id"));
		object.put("prevname", getpnReport(preobj).get("name"));
		object.put("nextid", getpnReport(nextobj).get("id"));
		object.put("nextname", getpnReport(nextobj).get("name"));
		return resultMessage(getImg(object));
	}

	public String insert(JSONObject info) {
		return bind().data(info).insertOnce().toString();
	}

	// 实名举报
	private String RealName(JSONObject object) {
		if (count > 5) {
			return resultMessage(0);
		}
		setCheck();
		form.putRule("Informant", formdef.notNull);
		form.putRule("InformantPhone", formdef.notNull);
		if (!form.checkRuleEx(object)) {
		}
		if (!checkPhone(object.get("InformantPhone").toString())) {
			return resultMessage(2);
		}
		// 发送短信验证码,中断当前操作
		// 获取随机6位验证码
		String ckcode = getValidateCode();
		// 1.发送验证码
		String text = "您的验证码为：" + ckcode;
		String message = appsProxy
				.proxyCall(
						host, "/45/SMS/SendVerity/"
								+ object.get("phone").toString() + "/" + text,
						null, "")
				.toString();
		long errorcode = (long) JSONHelper.string2json(message)
				.get("errorcode");
		int code = Integer.parseInt(String.valueOf(errorcode));
		// int code = SendVerity(object.get("phone").toString(),
		// "您的验证码为：" + ckcode);
		if (code == 0) {
			String nextstep = object.toString().replace("\"", "\\\"");
			boolean flag = interrupt._break(ckcode,
					object.get("InformantPhone").toString(),
					appid + "/45/Report/insert/s:" + nextstep, "13");
			code = flag ? 0 : 99;
		}

		return resultMessage(code, "验证码发送成功");
	}

	// 恢复当前操作
	public int resu(JSONObject object) {
		if (object == null) {
			return 10;
		}
		if (object.containsKey("ckcode") && object.containsKey("phone")) {
			int code = interrupt._resume(object.get("ckcode").toString(),
					object.get("phone").toString(),
					String.valueOf(appsProxy.appid()));
			if (code == 0) {
				return 4;
			}
			if (code == 1) {
				return 5;
			}
			return 0;
		}
		return 99;
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
	public String getId(String code,String url) {
		// 获取微信签名
		String signMsg = appsProxy
				.proxyCall(host, appid + "/30/Wechat/getSignature/"+url, null, "")
				.toString();
		String sign = JSONHelper.string2json(signMsg).get("message").toString();
		JSONObject object = new JSONObject();
		String openid = appsProxy.proxyCall(host,
				appid + "/30/Wechat/BindWechat/" + code, null, "").toString();
		if (openid.equals("")) {
			return jGrapeFW_Message.netMSG(7, "code错误");
		}
		// 将获取到的openid与库表中的openid进行比对，若存在已绑定，否则未绑定
		String message = appsProxy.proxyCall(host,
				appid + "/16/wechatUser/FindOpenId/" + openid, null, "")
				.toString();
		nlogger.logout("message:" + message);
		String tip = JSONHelper.string2json(message).get("message").toString();
		if (!("").equals(tip)) {
			object.put("msg", "已实名认证");
			object.put("openid", openid);
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
		System.out.println(info);
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

		int code = SendVerity(object.get("phone").toString(),
				"您的验证码为：" + ckcode);
		if (code == 0) {
			String nextstep = appid + "/16/wechatUser/insertOpenId/" + info;
			// 2.中断[参数：随机验证码，手机号，下一步操作，appid]
			boolean flag = interrupt._break(ckcode, phone, nextstep,
					appid + "");
			code = flag ? 0 : 99;
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
		return appsProxy.proxyCall(host, appid + "/16/wechatUser/KickUser/"
				+ openid + "/" + object.toString(), null, "").toString();
	}

	// 解封
	public String UserUnKick() {
		return appsProxy
				.proxyCall(host, appid + "/16/wechatUser/unkick/", null, "")
				.toString();
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
		JSONArray array2 = new JSONArray();
		for (int i = 0; i < array.size(); i++) {
			JSONObject object = (JSONObject) array.get(i);
			array2.add(getImg(object));
		}
		return array2;
	}

	@SuppressWarnings("unchecked")
	private JSONObject getImg(JSONObject object) {
		List<String> list = new ArrayList<String>();
		if (object.containsKey("attr1")) {
			list = getImgUrl(list, object.get("attr1").toString());
		}
		if (object.containsKey("attr2")) {
			list = getImgUrl(list, object.get("attr2").toString());
		}
		if (object.containsKey("attr3")) {
			list = getImgUrl(list, object.get("attr3").toString());
		}
		if (object.containsKey("attr4")) {
			list = getImgUrl(list, object.get("attr4").toString());
		}
		// 获取举报类型
		if (object.containsKey("type")) {
			object = getType(object);
		}
		if (list.size() != 0) {
			object.put("image", StringHelper.join(list));
		} else {
			object.put("image", "");
		}
		return object;
	}

	@SuppressWarnings("unchecked")
	private JSONObject getType(JSONObject object) {
		if ("0".equals(object.get("type").toString())) {
			object.put("ReportType", "");
		} else {
			String message = appsProxy
					.proxyCall(host,
							appsProxy.appid() + "/45/Rtype/findById/"
									+ object.get("type").toString(),
							null, "")
					.toString();
			String msg = JSONHelper.string2json(message).get("message")
					.toString();
			msg = JSONHelper.string2json(msg).get("records").toString();
			object.put("ReportType",
					JSONHelper.string2json(msg).get("TypeName").toString());
		}
		return object;
	}

	private List<String> getImgUrl(List<String> list, String imgId) {
		if (("").equals(imgId)) {
			return list;
		}
		String url = appsProxy
				.proxyCall(host, appid + "/24/Files/geturl/" + imgId, null, "")
				.toString();
		if (!("").equals(url)) {
			list.add(url);
		}
		return list;
	}

	private boolean checkPhone(String mob) {
		return checkHelper.checkMobileNumber(mob);
	}

	// 设置验证项
	private formHelper setCheck() {
		form.putRule("Wrongdoer", formdef.notNull);
		// form.putRule("WrongdoerSex", formdef.notNull);
		form.putRule("content", formdef.notNull);
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
			Iterator<Entry<String, Object>> iterator = map.entrySet()
					.iterator();
			while (iterator.hasNext()) {
				Map.Entry<String, Object> entry = (Map.Entry<String, Object>) iterator
						.next();
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
		default:
			msg = "其他操作异常";
			break;
		}
		return jGrapeFW_Message.netMSG(num, msg);
	}
}
