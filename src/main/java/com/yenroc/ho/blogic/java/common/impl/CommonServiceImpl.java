package com.yenroc.ho.blogic.java.common.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageHelper;
import com.yenroc.ho.blogic.java.common.AbstractCommonService;
import com.yenroc.ho.blogic.java.common.CommonService;
import com.yenroc.ho.common.bean.PageInfo;
import com.yenroc.ho.common.bean.Pub;
import com.yenroc.ho.common.consts.CommonConsts;
import com.yenroc.ho.common.context.BLogContext;
import com.yenroc.ho.common.context.SpringContextHolder;
import com.yenroc.ho.common.service.RedisService;
import com.yenroc.ho.mapper.sys.SysDelDataDao;
import com.yenroc.ho.mapper.base.entity.BaseEntity;
import com.yenroc.ho.mapper.sys.entity.SysDelData;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tk.mybatis.mapper.common.BaseMapper;

import java.util.*;

@Service("CommonService")
@Transactional(rollbackFor = Exception.class)
public class CommonServiceImpl extends AbstractCommonService implements CommonService {

    private static final Logger log = LoggerFactory.getLogger(CommonService.class);

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RedisService redisService;

    @Autowired
    private SysDelDataDao sysDelDataDao;

    public <T> List<T> select(String tableName, Map<String, Object> params) throws Exception {
        String tableEntityPath = checkTableName(tableName);
        BaseMapper mapper = SpringContextHolder.getBean(String.format("%sDao", tableName));
        params.put("isDel", 0);
        Object oid = params.get("id");
        Object o = objectMapper.convertValue(params, Class.forName(tableEntityPath));
        List<T> result = new ArrayList<>();
        if (oid != null && !"".equals(oid)) {
            String redisKey = String.format(CommonConsts.RedisKeyConsts.table_data_key, tableName, oid);
            if (redisService.exists(redisKey)) {
                T one = (T) redisService.get(redisKey);
                log.info("???redis????????????key=[{}]?????????=[{}]", redisKey, one);
                result.add(one);
            }  else {
                T one = (T) mapper.selectByPrimaryKey(o);
                if (one != null) {
                    log.info("???redis????????????,key=[{}],value=[{}]", redisKey, one);
                    redisService.set(redisKey, one, CommonConsts.RedisKeyConsts.expireTime_hours);
                    result.add(one);
                }
            }
        } else {
            PageInfo pageInfo = (PageInfo) BLogContext.getValue(CommonConsts.PAGE_INFO);
            if (pageInfo != null) {
                PageHelper.startPage(pageInfo.getPageNum(), pageInfo.getPageSize());
                if (StringUtils.isNotBlank(pageInfo.getOrderBy())) {
                    PageHelper.orderBy(pageInfo.getOrderBy());
                }
            }
            result = mapper.select(o);
            // ??????????????????
            Pub.setPageInfo(result);
        }
        return result;
    }

    public String insert(String tableName, Map<String, Object> params) throws Exception {
        String tableEntityPath = checkTableName(tableName);
        BaseMapper mapper = SpringContextHolder.getBean(String.format("%sDao", tableName));
        Object oid = params.get("id");
        if (oid == null || "".equals(oid)) {
            params.put("id", UUID.randomUUID().toString());
        }
        String id = params.get("id").toString();
        BaseEntity oInsert = (BaseEntity) objectMapper.convertValue(params, Class.forName(tableEntityPath));
        // ??????????????????,??????????????????
        oInsert.initInsert();

        int insert = mapper.insert(oInsert);
        if (insert > 0) {
            String redisKey = String.format(CommonConsts.RedisKeyConsts.table_data_key, tableName, oid);
            log.info("???redis????????????,key=[{}],value=[{}]", redisKey, oInsert);
            redisService.set(redisKey, oInsert, CommonConsts.RedisKeyConsts.expireTime_hours);
        }
        return id;
    }

    @Override
    public int update(String tableName, Map<String, Object> params) throws Exception {
        String tableEntityPath = checkTableName(tableName);
        BaseMapper mapper = SpringContextHolder.getBean(String.format("%sDao", tableName));
        String id = params.get("id").toString();
        Object oid = params.get("id");
        if (oid == null || "".equals(oid)) {
            return 0;
        }
        BaseEntity oUpdate = (BaseEntity) objectMapper.convertValue(params, Class.forName(tableEntityPath));
        oUpdate.initUpdate();
        int result = mapper.updateByPrimaryKeySelective(oUpdate);
        if (result > 0) {
            BaseEntity one = (BaseEntity) mapper.selectByPrimaryKey(oUpdate);
            if (one != null) {
                String redisKey = String.format(CommonConsts.RedisKeyConsts.table_data_key, tableName, oid);
                log.info("??????redis?????????,key=[{}],value=[{}]", redisKey, one);
                redisService.set(redisKey, one, CommonConsts.RedisKeyConsts.expireTime_hours);
            }
        }
        return result;
    }

    @Override
    public int delete(String tableName, Map<String, Object> params) throws Exception {
        String tableEntityPath = checkTableName(tableName);
        Object oid = params.get("id");
        if (oid == null || "".equals(oid)) {
            log.warn("id??????,????????????tableName=[{}]????????????", tableName);
            return 0;
        }
        BaseMapper mapper = SpringContextHolder.getBean(String.format("%sDao", tableName));
        Object oUpdate = objectMapper.convertValue(params, Class.forName(tableEntityPath));
        Object o = mapper.selectByPrimaryKey(oUpdate);
        if (o == null) {
            log.warn("tableName=[{}]????????????id=[{}]?????????, ???????????????", tableName, oid.toString());
            return 0;
        }
        Map<String, Object> delMap = new HashMap<>();
        delMap.put("id", oid);
        BaseEntity oDel = (BaseEntity) objectMapper.convertValue(delMap, Class.forName(tableEntityPath));
        oDel.initDelete();
        int result = mapper.updateByPrimaryKeySelective(oDel);

        // ??????????????????????????????
        SysDelData delData = new SysDelData();
        delData.initInsert();
        delData.setId(oid.toString());
        delData.setTableName(tableName);
        delData.setDataInfo(objectMapper.writeValueAsString(o));
        sysDelDataDao.insert(delData);

        String redisKey = String.format(CommonConsts.RedisKeyConsts.table_data_key, tableName, oid);
        log.info("???redis??????key=[{}]?????????", redisKey);
        redisService.remove(redisKey);
        return result;
    }

}
