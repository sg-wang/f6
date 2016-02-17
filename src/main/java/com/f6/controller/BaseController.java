package com.f6.controller;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.f6.auth.domain.UserVO;
import com.f6.exceptions.AuthenticationException;
import com.f6.exceptions.BadParameterException;
import com.f6.exceptions.BusinessException;
import com.f6.service.CommonService;
import com.f6.utils.DispatherConstant;
import com.f6.utils.F6SystemUtils;
import com.f6.utils.F6WebUtil;
import com.f6.utils.PasswordHelper;
import com.f6.utils.SystemConstans;
import com.f6.vo.DBParameter;

public abstract class BaseController {
	private Logger logger = LoggerFactory.getLogger(BaseController.class);
	@Autowired
	private CommonService commonservice;

	public abstract void authenticate(HttpServletRequest requset, HttpServletResponse response)
			throws AuthenticationException;

	public abstract void dataValidate(HttpServletRequest requset, HttpServletResponse response)
			throws BadParameterException;

	public abstract void postProcess(HttpServletRequest requset, HttpServletResponse response);

	@RequestMapping(value = "query", method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
	@ResponseBody
	public Map executeQuery(@RequestBody Map paramap, HttpServletRequest requset, HttpServletResponse response)
			throws AuthenticationException, BadParameterException, BusinessException {
		logger.debug("------------------execute----------------------------");
		baseValidate(paramap);
		dataValidate(requset, response);
		authenticate(requset, response);
		String result = query(paramap, requset, response);
		postProcess(requset, response);
		return F6WebUtil.buildResponseMap(SystemConstans.RESPONSE_LABEL_SUCCESS, result, "");
	}

	private void baseValidate(Map paramap) throws BadParameterException {
		String functionid = (String) paramap.get(SystemConstans.PARAM_FUNCTION_ID);
		logger.info("*******************BaseController=>******functionid****************************" + functionid);
		if (F6SystemUtils.isStrNull(functionid)) {
			throw new BadParameterException("");
		}
	}

	public abstract String query(Map paramap, HttpServletRequest requset, HttpServletResponse reponse)
			throws BusinessException;

	@RequestMapping(value = DispatherConstant.LOGOUT, method = RequestMethod.POST)
	public Map logout(HttpServletRequest req, HttpServletResponse res) {

		logger.info("*************************logout successful****************************");
		return F6WebUtil.buildResponseMap(SystemConstans.RESPONSE_LABEL_SUCCESS, "", "logout successful");
	}

	@RequestMapping(value = DispatherConstant.LOGIN, method = RequestMethod.POST)
	@ResponseBody
	public Map login(@RequestBody UserVO uservo, RedirectAttributes attrs, HttpServletRequest request)
			throws AuthenticationException, BusinessException {
		String username = uservo.getUserName();
		String password = uservo.getUserPassword();
		if (F6SystemUtils.isStrNull(username) || F6SystemUtils.isStrNull(password)) {
			return F6WebUtil.buildResponseMap(SystemConstans.RESPONSE_LABEL_NOAUTH, "", SystemConstans.ERROR_USER_PWD);
		}

		UserVO resultvo = null;
		String encryptedpwd = PasswordHelper.encryptString(password);

		Map<String, String> parametermap = new HashMap<String, String>();
		parametermap.put("identificationCode", username);

		DBParameter dbparam = F6SystemUtils.buildDBParameter("UserVO", "selectByIdentificationID", parametermap);

		Map<String, ?> dbresult = commonservice.queryOne(dbparam);
		if (dbresult == null || dbresult.size() == 0) {
			return F6WebUtil.buildResponseMap(SystemConstans.RESPONSE_LABEL_NOAUTH, "", SystemConstans.ERROR_USER_PWD);
		} else {
			String dbpwd = (String) dbresult.get("password");
			if (F6SystemUtils.isStrNull(dbpwd) || !encryptedpwd.equals(dbpwd)) {
				return F6WebUtil.buildResponseMap(SystemConstans.RESPONSE_LABEL_NOAUTH, "",
						SystemConstans.ERROR_USER_PWD);
			}

		}
		resultvo = (UserVO) F6SystemUtils.parseMap2Obj(dbresult, UserVO.class.getName());

		resultvo.setUserPassword("");

		String requestIP = request.getRemoteAddr();
		String token = PasswordHelper.generateToken(username + encryptedpwd + requestIP);
		resultvo.setToken(token);
		Map<String, String> tokenparam = new HashMap<String, String>();
		tokenparam.put("userid", username);
		tokenparam.put("token", token);

		DBParameter dbparameter = F6SystemUtils.buildDBParameter("TokenVOMapper", "updateToken", tokenparam);
		commonservice.change(dbparameter, SystemConstans.CHANGE_ACTION_UPDATE);

		return F6WebUtil.buildResponseMap(SystemConstans.RESPONSE_LABEL_SUCCESS, resultvo, "");
	}
}
