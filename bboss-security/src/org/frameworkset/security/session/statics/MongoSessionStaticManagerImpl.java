package org.frameworkset.security.session.statics;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.frameworkset.nosql.mongodb.MongoDBHelper;

import com.frameworkset.util.StringUtil;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.Mongo;

public class MongoSessionStaticManagerImpl implements SessionStaticManager {
	private Mongo mongoClient;
	private DB db = null;

	public MongoSessionStaticManagerImpl() {
		mongoClient = MongoDBHelper
				.getMongoClient(MongoDBHelper.defaultMongoDB);
		db = mongoClient.getDB("sessiondb");
	}

	@Override
	public List<SessionAPP> getSessionAPP() {
		List<SessionAPP> appList = new ArrayList<SessionAPP>();

		List<String> list = getAPPName();

		for (String appkey : list) {
			SessionAPP sessionApp = new SessionAPP();

			DBCollection coll = db.getCollection(appkey);

			sessionApp.setAppkey(appkey.substring(0,
					appkey.indexOf("_sessions")));
			sessionApp.setSessions(coll.getCount());

			appList.add(sessionApp);

		}

		return appList;
	}

	/**
	 * 获取当前db中以_sessions结尾的表名
	 * 
	 * @return 2014年6月5日
	 */
	public List<String> getAPPName() {

		List<String> appList = new ArrayList<String>();

		// 获取所有当前db所有信息集合
		Set<String> apps = db.getCollectionNames();

		if (apps == null || apps.size() == 0) {
			return null;
		}

		Iterator<String> itr = apps.iterator();

		while (itr.hasNext()) {

			String app = itr.next();

			if (app.endsWith("_sessions")) {
				appList.add(app);
			}

		}
		return appList;
	}

	@Override
	public List<SessionInfo> getAllSessionInfos(Map queryParams, int row,
			int page) throws Exception {
		List<SessionInfo> sessionList = new ArrayList<SessionInfo>();

		String appKey = queryParams.get("appKey") + "";
		if (StringUtil.isEmpty(appKey)) {
			return null;
		}

		// 获取当前表
		DBCollection sessions = db.getCollection(MongoDBHelper
				.getAppSessionTableName(appKey));
		sessions.ensureIndex("sessionid");

		// 查询条件
		BasicDBObject query = new BasicDBObject();

		String sessionid = (String) queryParams.get("sessionid");
		if (!StringUtil.isEmpty(sessionid)) {
			query.append("sessionid", sessionid);
		}

		SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
		String createtime_start = (String) queryParams.get("createtime_start");
		String createtime_end = (String) queryParams.get("createtime_end");

		if (!StringUtil.isEmpty(createtime_start)
				&& !StringUtil.isEmpty(createtime_end)) {

			Date start_date = sdf.parse(createtime_start);
			Date end_date = sdf.parse(createtime_end);

			query.append("creationTime",
					new BasicDBObject("$gte", start_date.getTime()).append(
							"$lte", end_date.getTime()));
		} else if (!StringUtil.isEmpty(createtime_start)) {
			Date date = sdf.parse(createtime_start);
			query.append("creationTime",
					new BasicDBObject("$gte", date.getTime()));
		} else if (!StringUtil.isEmpty(createtime_end)) {
			Date date = sdf.parse(createtime_end);

			query.append("creationTime",
					new BasicDBObject("$lte", date.getTime()));
		}

		String host = (String) queryParams.get("host");
		if (!StringUtil.isEmpty(host)) {
			Pattern hosts = Pattern.compile("^.*" + host + ".*$",
					Pattern.CASE_INSENSITIVE);
			query.append("host", new BasicDBObject("$regex",hosts));
		}

		String referip = (String) queryParams.get("referip");
		if (!StringUtil.isEmpty(referip)) {
			Pattern referips = Pattern.compile("^.*" + referip + ".*$",
					Pattern.CASE_INSENSITIVE);
			query.append("referip", new BasicDBObject("$regex",referips));
		}

		String validate = (String) queryParams.get("validate");
		if (!StringUtil.isEmpty(validate)) {
			boolean _validate = Boolean.parseBoolean(validate);
			query.append("_validate", _validate);
		}

		// 显示字段
		BasicDBObject keys = new BasicDBObject();
		keys.put("appKey", 1);
		keys.put("sessionid", 1);
		keys.put("creationTime", 1);
		keys.put("lastAccessedTime", 1);
		keys.put("maxInactiveInterval", 1);
		keys.put("referip", 1);
		keys.put("_validate", 1);
		keys.put("host", 1);
		keys.put("requesturi", 1);
		DBCursor cursor = sessions.find(query, keys).skip(page).limit(row)
				.sort(new BasicDBObject("creationTime", -1));// 1升序，-1降序
		try {

			while (cursor.hasNext()) {
				DBObject dbobject = cursor.next();

				SessionInfo info = new SessionInfo();

				info.setAppKey(appKey);

				info.setSessionid(dbobject.get("sessionid") + "");

				String creationTime = dbobject.get("creationTime") + "";
				if (!StringUtil.isEmpty(creationTime)) {
					Date creationTimeDate = new Date(
							Long.parseLong(creationTime));
					info.setCreationTime(creationTimeDate);
				}

				String lastAccessedTime = dbobject.get("lastAccessedTime") + "";
				if (!StringUtil.isEmpty(lastAccessedTime)) {
					Date lastAccessedTimeDate = new Date(
							Long.parseLong(lastAccessedTime));
					info.setLastAccessedTime(lastAccessedTimeDate);
				}

				String maxInactiveInterval = dbobject
						.get("maxInactiveInterval") + "";
				if (!StringUtil.isEmpty(maxInactiveInterval)) {
					info.setMaxInactiveInterval(Long
							.parseLong(maxInactiveInterval));
				}

				info.setReferip(dbobject.get("referip") + "");
				info.setValidate((Boolean) dbobject.get("_validate"));
				info.setHost(dbobject.get("host") + "");
				info.setRequesturi((String)dbobject.get("requesturi"));
				sessionList.add(info);
			}
		} finally {
			cursor.close();
		}

		return sessionList;
	}

	@Override
	public SessionInfo getSessionInfo(String appKey, String sessionid) {

		if (!StringUtil.isEmpty(appKey) && !StringUtil.isEmpty(sessionid)) {
			// 获取当前表
			DBCollection sessions = db.getCollection(MongoDBHelper
					.getAppSessionTableName(appKey));
			sessions.ensureIndex("sessionid");

			// 查询条件
			BasicDBObject query = new BasicDBObject();

			if (!StringUtil.isEmpty(sessionid)) {
				query.append("sessionid", sessionid);
			}

			// 显示字段
			BasicDBObject keys = new BasicDBObject();

			DBObject obj = sessions.findOne(query, keys);

			if (obj == null) {
				return null;
			} else {
				SessionInfo info = new SessionInfo();
				info.setMaxInactiveInterval((Long) obj
						.get("maxInactiveInterval"));
				info.setAppKey(appKey);

				String creationTime = obj.get("creationTime") + "";
				if (!StringUtil.isEmpty(creationTime)) {
					Date creationTimeDate = new Date(
							Long.parseLong(creationTime));
					info.setCreationTime(creationTimeDate);
				}

				String lastAccessedTime = obj.get("lastAccessedTime") + "";
				if (!StringUtil.isEmpty(lastAccessedTime)) {
					Date lastAccessedTimeDate = new Date(
							Long.parseLong(lastAccessedTime));
					info.setLastAccessedTime(lastAccessedTimeDate);
				}

				info.setSessionid(sessionid);
				info.setReferip((String) obj.get("referip"));
				info.setValidate((Boolean) obj.get("_validate"));
				info.setHost((String) obj.get("host"));
				info.setRequesturi((String)obj.get("requesturi"));
				Map<String, Object> attributes = MongoDBHelper
						.toMap(obj, false);
				info.setAttributes(attributes);

				return info;
			}
		} else {

			return null;
		}

	}

	@Override
	public void removeSessionInfo(String appKey, String sessionid) {
		if (!StringUtil.isEmpty(appKey) && !StringUtil.isEmpty(sessionid)) {

			DBCollection sessions = db.getCollection(MongoDBHelper
					.getAppSessionTableName(appKey));
			sessions.ensureIndex("sessionid");

			// 条件
			BasicDBObject wheresql = new BasicDBObject();
			wheresql.append("sessionid", sessionid);

			sessions.remove(wheresql);

		}
	}

	@Override
	public void removeSessionInfos(String appKey, String[] sessionids) {
		if (!StringUtil.isEmpty(appKey)) {

			for (String sessionid : sessionids) {
				if (!StringUtil.isEmpty(sessionid)) {

					removeSessionInfo(appKey, sessionid);
				}

			}
		}
	}

	@Override
	public void removeAllSession(String appKey) {
		if (!StringUtil.isEmpty(appKey)) {

			DBCollection sessions = db.getCollection(MongoDBHelper
					.getAppSessionTableName(appKey));

			// 条件
			BasicDBObject wheresql = new BasicDBObject();

			sessions.remove(wheresql);

		}

	}

	public static void main(String[] args) {
		MongoSessionStaticManagerImpl smsi = new MongoSessionStaticManagerImpl();

		// 应用列表
		List<SessionAPP> list = smsi.getSessionAPP();
		for (SessionAPP app : list) {
			System.out.println(app.getAppkey() + "==" + app.getSessions());
		}

		// 应用对应的session列表
		Map queryParams = new HashMap();
		queryParams.put("appKey", "SanyPDP");
		// queryParams.put("host", "10.8.198.108-BPITGW-TANX");
		// queryParams.put("createtime_start", "2014/06/05 16:15:10");
		// queryParams.put("createtime_end", "2014/06/05 19:05:00");

		// queryParams.put("validate", "true");

		queryParams.put("sessionid", "0c11f3c8");
		try {
			List<SessionInfo> infolist = smsi.getAllSessionInfos(queryParams,
					6, 1);
			for (SessionInfo info : infolist) {
				System.out.println("appkey=" + info.getAppKey() + ",host="
						+ info.getHost() + ",MaxInactiveInterval="
						+ info.getMaxInactiveInterval() + ",Referip="
						+ info.getReferip() + ",Sessionid="
						+ info.getSessionid() + ",CreationTime="
						+ info.getCreationTime() + ",LastAccessedTime="
						+ info.getLastAccessedTime() + "validate="
						+ info.isValidate());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		// smsi.removeAllSession("SanyPDP");

		// 删除session
		// smsi.removeSessionInfo("SanyPDP",
		// "7f860398-c7b4-461b-ab63-281145dabbf4");

		// SessionInfo info = smsi.getSessionInfo("SanyPDP",
		// "751f3a63-fba2-4372-89ed-ea8957a8eb11");
		// System.out.println("appkey==" + info.getAppKey() + ",host=="
		// + info.getHost() + ",MaxInactiveInterval=="
		// + info.getMaxInactiveInterval() + ",Referip=="
		// + info.getReferip() + ",Sessionid==" + info.getSessionid()
		// + ",CreationTime==" + info.getCreationTime()
		// + ",LastAccessedTime==" + info.getLastAccessedTime()
		// + "validate==" + info.isValidate());
		// Map<String, Object> attributes = info.getAttributes();
		//
		// Set set = attributes.keySet();
		// if (set != null && set.size() > 0) {
		// Iterator it = set.iterator();
		// while (it.hasNext()) {
		// String key = (String) it.next();
		// String value = (String) attributes.get(key);
		// System.out.println("key=" + key + "  value=" + value);
		// }
		// }

	}

}
