package com.bird.web.sso.server;

import com.bird.web.sso.server.ticket.ITicketProtector;
import com.bird.web.sso.server.ticket.ITicketSessionStore;
import com.bird.web.sso.ticket.TicketInfo;
import com.bird.web.sso.utils.CookieHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Date;

/**
 * @author liuxx
 * @date 2019/3/1
 */
public class SsoServer {

    private SsoServerProperties serverProperties;

    private ITicketSessionStore sessionStore;
    private ITicketProtector protector;

    public SsoServer(SsoServerProperties serverProperties, ITicketSessionStore sessionStore) {
        this.serverProperties = serverProperties;
        this.sessionStore = sessionStore;
    }

    public SsoServer(SsoServerProperties serverProperties, ITicketProtector protector) {
        this.serverProperties = serverProperties;
        this.protector = protector;
    }

    /**
     * 登录，将token写入cookie
     *
     * @param ticketInfo 票据信息
     * @return token
     */
    public String login(HttpServletResponse response, TicketInfo ticketInfo) {
        if (ticketInfo.getExpireTime() == null) {
            Date creationTime = ticketInfo.getCreationTime();
            long expire = creationTime.getTime() + serverProperties.getExpire() * 60 * 1000L;
            ticketInfo.setExpireTime(new Date(expire));
        }
        String token = serverProperties.getUseSessionStore()
                ? sessionStore.storeTicket(ticketInfo)
                : protector.protect(ticketInfo);

        //用户中心写入Cookie
        CookieHelper.setCookie(response, serverProperties.getCookieName(), StringUtils.strip(token), serverProperties.getExpire() * 60);
        return token;
    }

    /**
     * 注销
     * 1、清除SessionStore；2、清除Cookie
     *
     * @return
     */
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String token = getToken(request);

        if (!StringUtils.isEmpty(token)) {
            //清除SessionStore
            if (sessionStore != null) {
                sessionStore.removeTicket(token);
            }

            //清除Cookie
            CookieHelper.removeCookie(request, response, serverProperties.getCookieName());
        }
    }

    /**
     * 根据token获取票据信息
     * @param token token
     * @return 票据
     */
    public TicketInfo getTicket(String token) {
        if (StringUtils.isBlank(token)) return null;

        TicketInfo ticketInfo;
        if (serverProperties.getUseSessionStore()) {
            ticketInfo = sessionStore.getTicket(token);
            if (ticketInfo != null && serverProperties.getAutoRefresh()) {
                //如果超过一半的有效期，则刷新
                Date now = new Date();
                Date issuedTime = ticketInfo.getLastRefreshTime();
                Date expireTime = ticketInfo.getExpireTime();

                long t1 = now.getTime() - issuedTime.getTime();
                long t2 = expireTime.getTime() - now.getTime();
                if (t1 > t2) {
                    ticketInfo = sessionStore.refreshTicket(token, ticketInfo, t1 + t2);
                    //todo : throw event
                }
            }
        } else {
            ticketInfo = protector.unProtect(token);
        }
        return ticketInfo;
    }

    /**
     * 从HttpServletRequest中获取token
     *
     * @param request
     * @return
     */
    private String getToken(HttpServletRequest request) {
        //先从header中获取token
        String token = request.getHeader(serverProperties.getCookieName());
        if (StringUtils.isBlank(token)) {
            token = CookieHelper.getCookieValue(request, serverProperties.getCookieName());
        }
        return token;
    }
}