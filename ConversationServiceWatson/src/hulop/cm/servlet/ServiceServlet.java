/*******************************************************************************
 * Copyright (c) 2014, 2017  IBM Corporation, Carnegie Mellon University and others
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *******************************************************************************/

package hulop.cm.servlet;

import java.io.IOException;
import java.util.Iterator;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.wink.json4j.JSONArray;
import org.apache.wink.json4j.JSONException;
import org.apache.wink.json4j.JSONObject;

import hulop.cm.qa.WatsonHelper;
import hulop.cm.util.CommonUtil;
import hulop.cm.util.LogHelper;

/**
 * Servlet implementation class ServiceServlet
 */
@WebServlet("/service")
public class ServiceServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;

	private LogHelper logHelper = new LogHelper();
	private JSONObject mLastResultMap = new JSONObject();

	/**
	 * @see HttpServlet#HttpServlet()
	 */
	public ServiceServlet() {
		super();
	}

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse
	 *      response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		request.setCharacterEncoding("UTF-8");
		String id = request.getParameter("id");
		if (id == null || id.isEmpty()) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}
		String lang = request.getParameter("lang");
		try {
			CommonUtil.getConfig().getJSONObject("watson_config").getString("workspace_" + lang).charAt(0);
		} catch (Exception e) {
			lang = "en";
		}
		final String clientId = id != null ? id : request.getSession(true).getId();
		final String text = request.getParameter("text");
		final Object bodyObj = CommonUtil.getJSON(request);
		if (bodyObj != null) {
			System.out.println(bodyObj);
		}
		System.out.println(text);
		WatsonHelper watHelper = WatsonHelper.getInstance(lang);
		JSONObject result = null;
		try {
			if (bodyObj instanceof JSONObject) {
				try {
					JSONObject lastResult = watHelper.getLastResult(clientId);
					if (lastResult != null) {
						JSONObject lastContext = lastResult.getJSONObject("context");
						JSONObject context = ((JSONObject) bodyObj).getJSONObject("context");
						for (Iterator<String> it = context.keys(); it.hasNext();) {
							String key = it.next();
							lastContext.put(key, context.get(key));
						}
						watHelper.setLastResult(clientId, lastResult);
					}
				} catch (Exception e) {
				}
			}
			try {
				result = watHelper.postMessage(clientId, text);
			} catch (Exception e) {
				e.printStackTrace();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		try {
			int errorCount = 0;
			JSONObject lastResult = result;
			if (result == null) {
				try {
					try {
						errorCount = mLastResultMap.getJSONObject(clientId).getJSONObject("context")
								.getInt("error_count");
					} catch (Exception e) {
					}
					if (errorCount++ < 1) {
						lastResult = simpleResult(text, "ja".equals(lang) ? "接続に問題が発生しました。もう一度お話し下さい"
								: "A connection error occurred. Please say it again.", lang, false);
					} else {
						errorCount = 0;
						lastResult = simpleResult(text,
								"ja".equals(lang) ? "現在、対話を行うことが出来ません。あとでもう一度お試しください"
										: "Conversation service temporarily unavailable. Please try again later",
								lang, true);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			if (lastResult != null) {
				if (lastResult.has("context")) {
					lastResult.getJSONObject("context").put("error_count", errorCount);
				}
				CommonUtil.sendJSON(lastResult, response);
				mLastResultMap.put(clientId, lastResult);
				try {
					logHelper.saveLog(clientId, lastResult);
				} catch (Exception e) {
					e.printStackTrace();
				}
				return;
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
		response.sendError(HttpServletResponse.SC_NOT_FOUND);
	}

	private JSONObject simpleResult(String input_text, String output_text, String lang, boolean finish)
			throws Exception {
		JSONObject input = new JSONObject();
		if (input_text != null) {
			input.put("text", input_text);
		}
		JSONObject output = new JSONObject();
		output.put("text", new JSONArray(output_text.split("\n")));
		output.put("log_messages", new JSONArray());
		output.put("nodes_visited", new JSONArray());

		JSONObject context = new JSONObject();
		context.put("conversation_id", "");
		context.put("agent_name", "ja".equals(lang) ? "エラー" : "Error");
		context.put("finish", finish);
		context.put("system", new JSONObject().put("dialog_request_counter", 1).put("dialog_turn_counter", 1));

		JSONObject result = new JSONObject();
		result.put("output", output);
		result.put("input", input);
		result.put("context", context);
		result.put("entities", new JSONArray());
		result.put("intents", new JSONArray());
		return result;
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse
	 *      response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		doGet(request, response);
	}

}
