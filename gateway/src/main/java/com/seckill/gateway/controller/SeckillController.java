package com.seckill.gateway.controller;


import com.seckill.common.api.cache.RedisServiceApi;
import com.seckill.common.api.cache.vo.GoodsKeyPrefix;
import com.seckill.common.api.cache.vo.OrderKeyPrefix;
import com.seckill.common.api.cache.vo.SkKeyPrefix;
import com.seckill.common.api.goods.GoodsServiceApi;
import com.seckill.common.api.goods.vo.GoodsVo;
import com.seckill.common.api.mq.MqProviderApi;
import com.seckill.common.api.mq.vo.SkMessage;
import com.seckill.common.api.order.OrderServiceApi;
import com.seckill.common.api.seckill.SeckillServiceApi;
import com.seckill.common.api.seckill.vo.VerifyCodeVo;
import com.seckill.common.api.user.vo.UserVo;
import com.seckill.common.domain.SeckillOrder;
import com.seckill.common.result.CodeMsg;
import com.seckill.common.result.Result;
import com.seckill.common.util.MD5Util;
import com.seckill.common.util.UUIDUtil;
import com.seckill.common.util.VerifyCodeUtil;
import com.seckill.gateway.config.access.AccessLimit;
import org.apache.dubbo.config.annotation.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.imageio.ImageIO;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * 秒杀接口
 *
 * @author noodle
 */
@Controller
@RequestMapping("/seckill/")
public class SeckillController implements InitializingBean {

    private static Logger logger = LoggerFactory.getLogger(SeckillController.class);

    @Reference(interfaceClass = RedisServiceApi.class)
    RedisServiceApi redisService;

    @Reference(interfaceClass = GoodsServiceApi.class)
    GoodsServiceApi goodsService;

    @Reference(interfaceClass = SeckillServiceApi.class)
    SeckillServiceApi seckillService;

    @Reference(interfaceClass = OrderServiceApi.class)
    OrderServiceApi orderService;

    @Reference(interfaceClass = MqProviderApi.class)
    MqProviderApi sender;

    /**
     * 用于内存标记，标记库存是否为空，从而减少对redis的访问
     */
    private Map<Long, Boolean> localOverMap = new HashMap<>();

    /**
     * 获取秒杀接口地址
     * 1. 每一次点击秒杀，都会生成一个随机的秒杀地址返回给客户端
     * 2. 对秒杀的次数做限制（通过自定义拦截器注解完成）
     *
     * @param model
     * @param user
     * @param goodsId    秒杀的商品id
     * @param verifyCode 验证码
     * @return 被隐藏的秒杀接口路径
     */
    @AccessLimit(seconds = 5, maxAccessCount = 5, needLogin = true)
    @RequestMapping(value = "path", method = RequestMethod.GET)
    @ResponseBody
    public Result<String> getSeckillPath(Model model, UserVo user,
                                         @RequestParam("goodsId") long goodsId,
                                         @RequestParam(value = "verifyCode", defaultValue = "0") int verifyCode) {

        /** 在执行下面的逻辑之前，会先对path请求进行拦截处理（@AccessLimit， AccessInterceptor），防止访问次数过于频繁，对服务器造成过大的压力 */

        model.addAttribute("user", user);

        if (goodsId <= 0) {
            return Result.error(CodeMsg.SECKILL_PARM_ILLEGAL.fillArgs("商品id小于0"));
        }

        // 校验验证码
        boolean check = this.checkVerifyCode(user, goodsId, verifyCode);

        if (!check)
            return Result.error(CodeMsg.VERITF_FAIL);// 检验不通过，请求非法

        // 检验通过，获取秒杀路径
        String path = this.createSkPath(user, goodsId);

        // 向客户端回传随机生成的秒杀地址
        return Result.success(path);
    }

    /**
     * 秒杀逻辑（页面静态化分离，不需要直接将页面返回给客户端，而是返回客户端需要的页面动态数据，返回数据时json格式）
     * <p>
     * QPS:1306
     * 5000 * 10
     * <p>
     * GET/POST的@RequestMapping是有区别的
     * <p>
     * 通过随机的path，客户端隐藏秒杀接口
     * <p>
     * 优化: 不同于每次都去数据库中读取秒杀订单信息，而是在第一次生成秒杀订单成功后，
     * 将订单存储在redis中，再次读取订单信息的时候就直接从redis中读取
     *
     * @param model
     * @param user
     * @param goodsId
     * @param path    隐藏的秒杀地址，为客户端回传的path，最初也是有服务端产生的
     * @return 订单详情或错误码
     */
    // {path}为客户端回传的path，最初也是有服务端产生的
    @RequestMapping(value = "{path}/doSeckill", method = RequestMethod.POST)
    @ResponseBody
    public Result<Integer> doSeckill(Model model, UserVo user,
                                     @RequestParam("goodsId") long goodsId,
                                     @PathVariable("path") String path) {

        model.addAttribute("user", user);

        // 验证path是否正确
        boolean check = this.checkPath(user, goodsId, path);

        if (!check)
            return Result.error(CodeMsg.REQUEST_ILLEGAL);// 请求非法

        // 通过内存标记，减少对redis的访问，秒杀未结束才继续访问redis
        Boolean over = localOverMap.get(goodsId);
        if (over)
            return Result.error(CodeMsg.SECKILL_OVER);

        // 预减库存，同时在库存为0时标记该商品已经结束秒杀
        Long stock = redisService.decr(GoodsKeyPrefix.GOODS_STOCK, "" + goodsId);
        if (stock < 0) {
            localOverMap.put(goodsId, true);// 秒杀结束。标记该商品已经秒杀结束
            return Result.error(CodeMsg.SECKILL_OVER);
        }

        // 判断是否重复秒杀
        // 从redis中取缓存，减少数据库的访问
        SeckillOrder order = redisService.get(OrderKeyPrefix.SK_ORDER, ":" + user.getUuid() + "_" + goodsId, SeckillOrder.class);
        // 如果缓存中不存该数据，则从数据库中取
        if (order == null) {
            order = orderService.getSeckillOrderByUserIdAndGoodsId(user.getUuid(), goodsId);
        }

        if (order != null) {
            return Result.error(CodeMsg.REPEATE_SECKILL);
        }

        // 商品有库存且用户为秒杀商品，则将秒杀请求放入MQ
        SkMessage message = new SkMessage();
        message.setUser(user);
        message.setGoodsId(goodsId);

        // 放入MQ(对秒杀请求异步处理，直接返回)
        sender.sendSkMessage(message);

        // 排队中
        return Result.success(0);
    }

    /**
     * 用于返回用户秒杀的结果
     *
     * @param model
     * @param user
     * @param goodsId
     * @return orderId：成功, -1：秒杀失败, 0： 排队中
     */
    @RequestMapping(value = "result", method = RequestMethod.GET)
    @ResponseBody
    public Result<Long> getSeckillResult(Model model,
                                         UserVo user,
                                         @RequestParam("goodsId") long goodsId) {

        model.addAttribute("user", user);

        long result = seckillService.getSeckillResult(user.getUuid(), goodsId);
        return Result.success(result);
    }

    /**
     * goods_detail.htm: $("#verifyCodeImg").attr("src", "/seckill/verifyCode?goodsId=" + $("#goodsId").val());
     * 使用HttpServletResponse的输出流返回客户端异步获取的验证码（异步获取的代码如上所示）
     *
     * @param response
     * @param user
     * @param goodsId
     * @return
     */
    @RequestMapping(value = "verifyCode", method = RequestMethod.GET)
    @ResponseBody
    public Result<String> getVerifyCode(HttpServletResponse response, UserVo user,
                                        @RequestParam("goodsId") long goodsId) {
        logger.info("获取验证码");
        if (user == null || goodsId <= 0) {
            return Result.error(CodeMsg.SESSION_ERROR);
        }

        // 刷新验证码的时候置缓存中的随机地址无效
        String path = redisService.get(SkKeyPrefix.SK_PATH, "" + user.getUuid() + "_" + goodsId, String.class);
        if (path != null)
            redisService.delete(SkKeyPrefix.SK_PATH, "" + user.getUuid() + "_" + goodsId);

        // 创建验证码
        try {
            // String verifyCodeJsonString = seckillService.createVerifyCode(user, goodsId);
            VerifyCodeVo verifyCode = VerifyCodeUtil.createVerifyCode();

            // 验证码结果预先存到redis中
            redisService.set(SkKeyPrefix.VERIFY_RESULT, user.getUuid() + "_" + goodsId, verifyCode.getExpResult());
            ServletOutputStream out = response.getOutputStream();
            // 将图片写入到resp对象中
            ImageIO.write(verifyCode.getImage(), "JPEG", out);
            out.close();
            out.flush();

            return null;

        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(CodeMsg.SECKILL_FAIL);
        }
    }

    /**
     * 检验检验码的计算结果
     *
     * @param user
     * @param goodsId
     * @param verifyCode
     * @return
     */
    private boolean checkVerifyCode(UserVo user, long goodsId, int verifyCode) {
        if (user == null || goodsId <= 0) {
            return false;
        }

        // 从redis中获取验证码计算结果
        Integer oldCode = redisService.get(SkKeyPrefix.VERIFY_RESULT, user.getUuid() + "_" + goodsId, Integer.class);
        if (oldCode == null || oldCode - verifyCode != 0) {// !!!!!!
            return false;
        }

        // 如果校验成功，则说明校验码过期，删除校验码缓存，防止重复提交同一个验证码
        redisService.delete(SkKeyPrefix.VERIFY_RESULT, user.getUuid() + "_" + goodsId);
        return true;
    }

    /**
     * 创建秒杀地址, 并将其存储在redis中
     *
     * @param user
     * @param goodsId
     * @return
     */
    public String createSkPath(UserVo user, long goodsId) {

        if (user == null || goodsId <= 0) {
            return null;
        }

        // 随机生成秒杀地址
        String path = MD5Util.md5(UUIDUtil.uuid() + "123456");
        // 将随机生成的秒杀地址存储在redis中（保证不同的用户和不同商品的秒杀地址是不一样的）
        redisService.set(SkKeyPrefix.SK_PATH, "" + user.getUuid() + "_" + goodsId, path);
        return path;
    }

    /**
     * 验证路径是否正确
     *
     * @param user
     * @param goodsId
     * @param path
     * @return
     */
    public boolean checkPath(UserVo user, long goodsId, String path) {
        if (user == null || path == null)
            return false;
        // 从redis中读取出秒杀的path变量是否为本次秒杀操作执行前写入redis中的path
        String oldPath = redisService.get(SkKeyPrefix.SK_PATH, "" + user.getUuid() + "_" + goodsId, String.class);

        return path.equals(oldPath);
    }

    /**
     * 服务器程序启动的时候加载商品列表信息
     */
    @Override
    public void afterPropertiesSet() {

        List<GoodsVo> goods = goodsService.listGoodsVo();
        if (goods == null) {
            return;
        }

        // 将商品的库存信息存储在redis中
        for (GoodsVo good : goods) {
            redisService.set(GoodsKeyPrefix.GOODS_STOCK, "" + good.getId(), good.getStockCount());
            // 在系统启动时，标记库存不为空
            localOverMap.put(good.getId(), false);
        }
    }
}











//import com.seckill.common.api.cache.RedisServiceApi;
//import com.seckill.common.api.cache.vo.GoodsKeyPrefix;
//import com.seckill.common.api.cache.vo.OrderKeyPrefix;
//import com.seckill.common.api.cache.vo.SkKeyPrefix;
//import com.seckill.common.api.goods.GoodsServiceApi;
//import com.seckill.common.api.goods.vo.GoodsVo;
//import com.seckill.common.api.mq.MqProviderApi;
//import com.seckill.common.api.mq.vo.SkMessage;
//import com.seckill.common.api.order.OrderServiceApi;
//import com.seckill.common.api.seckill.SeckillServiceApi;
//import com.seckill.common.api.seckill.vo.VerifyCodeVo;
//import com.seckill.common.api.user.vo.UserVo;
//import com.seckill.common.domain.SeckillOrder;
//import com.seckill.common.result.CodeMsg;
//import com.seckill.common.result.Result;
//import com.seckill.common.util.MD5Util;
//import com.seckill.common.util.UUIDUtil;
//import com.seckill.common.util.VerifyCodeUtil;
//import com.seckill.gateway.config.access.AccessLimit;
//import lombok.extern.slf4j.Slf4j;
//import org.apache.dubbo.config.annotation.Reference;
//import org.springframework.beans.factory.InitializingBean;
//import org.springframework.stereotype.Controller;
//import org.springframework.ui.Model;
//import org.springframework.web.bind.annotation.*;
//
//import javax.imageio.ImageIO;
//import javax.servlet.ServletOutputStream;
//import javax.servlet.http.HttpServletResponse;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
//@Controller
//@RequestMapping("/seckill")
//@Slf4j
//public class SeckillController implements InitializingBean
//{
//
//    @Reference(interfaceClass = RedisServiceApi.class)
//    RedisServiceApi redisService;
//
//    @Reference(interfaceClass = GoodsServiceApi.class)
//    GoodsServiceApi goodsService;
//
//    @Reference(interfaceClass = OrderServiceApi.class)
//    OrderServiceApi orderService;
//
//    @Reference(interfaceClass = MqProviderApi.class)
//    MqProviderApi mqProvider;
//
//    @Reference(interfaceClass = SeckillServiceApi.class)
//    SeckillServiceApi seckillService;
//
//    //标记商品是否为空  false不为空  true为空
//    private Map<Long, Boolean> localOverMap = new HashMap<>();
//
//
//    @AccessLimit(seconds = 5, maxAccessCount = 5, needLogin = true)
//    @GetMapping("/path")
//    @ResponseBody
//    public Result<String> getSeckillPath(Model model, UserVo userVo, @RequestParam("goodsId") long goodsId,
//                                         @RequestParam(value = "verifyCode", defaultValue = "0") int verifyCode)
//    {
//        //@AccessLimit  防止频繁访问
//        model.addAttribute("user", userVo);
//
//        if (goodsId < 0)
//            return Result.error(CodeMsg.SECKILL_PARM_ILLEGAL.fillArgs("商品id出错"));
//
//        // 校验验证码
//        boolean check = this.checkVerifyCode(userVo, goodsId, verifyCode);
//
//        if (!check)
//            return Result.error(CodeMsg.VERITF_FAIL);// 检验不通过，请求非法
//
//        // 检验通过，获取秒杀路径
//        String path = this.createSkPath(userVo, goodsId);
//
//        // 向客户端回传随机生成的秒杀地址
//        return Result.success(path);
//    }
//
//    /**
//     * 进行秒杀的逻辑
//     *
//     * @param model
//     * @param userVo
//     * @param goodsId
//     * @param path
//     * @return
//     */
//    @PostMapping("/{path}/doSeckill")
//    @ResponseBody
//    public Result<Integer> doSeckill(Model model, UserVo userVo, @RequestParam("goodsId") long goodsId,
//                                     @PathVariable("path") String path)
//    {
//        model.addAttribute("user", userVo);
//
//        boolean check = this.checkPath(userVo, goodsId, path);
//
//        //非法请求 秒杀地址path出错
//        if (!check)
//            return Result.error(CodeMsg.REQUEST_ILLEGAL);
//
//        //商品是否为空
//        Boolean over = localOverMap.get(goodsId);
//        if (over)
//            return Result.error(CodeMsg.SECKILL_OVER);
//
//        //减库存
//        Long stock = redisService.decr(GoodsKeyPrefix.GOODS_STOCK, "" + goodsId);
//
//        System.out.println("stock:"+stock);
//        if (stock < 0)
//        {
//            localOverMap.put(goodsId, true);
//            return Result.error(CodeMsg.SECKILL_OVER);
//        }
//
//        //获取秒杀订单 判定是否重复秒杀
//        SeckillOrder seckillOrder = redisService.get(OrderKeyPrefix.SK_ORDER, ":" + userVo.getUuid() + "_" + goodsId, SeckillOrder.class);
//
//        System.out.println("seckillOrder:"+seckillOrder);
//        if (seckillOrder != null)
//            return Result.error(CodeMsg.REPEATE_SECKILL);
//
//        //发送消息至消息队列  完成秒杀
//        SkMessage message = new SkMessage();
//        message.setUser(userVo);
//        message.setGoodsId(goodsId);
//        mqProvider.sendSkMessage(message);
//
//        return Result.success(0);
//    }
//
//    /**
//     * 创建秒杀地址
//     *
//     * @param userVo
//     * @param goodsId
//     * @return
//     */
//    private String createSkPath(UserVo userVo, long goodsId)
//    {
//        if (userVo == null || goodsId <= 0)
//        {
//            return null;
//        }
//
//        // 随机生成秒杀地址
//        String path = MD5Util.md5(UUIDUtil.uuid() + "123456");
//        // 将随机生成的秒杀地址存储在redis中（保证不同的用户和不同商品的秒杀地址是不一样的）
//        redisService.set(SkKeyPrefix.SK_PATH, "" + userVo.getUuid() + "_" + goodsId, path);
//        return path;
//    }
//
//    /**
//     * 获取秒杀结果
//     *
//     * @param model
//     * @param userVo
//     * @param goodsId
//     * @return 其他 成功 -1失败 0排队
//     */
//    @GetMapping("/result")
//    @ResponseBody
//    public Result<Long> getSeckillResult(Model model, UserVo userVo, @RequestParam("goodsId") long goodsId)
//    {
//        model.addAttribute("user", userVo);
//
//        long result = seckillService.getSeckillResult(userVo.getUuid(), goodsId);
//
//        return Result.success(result);
//    }
//
//    /**
//     * 核对秒杀地址
//     *
//     * @param userVo
//     * @param goodsId
//     * @param path
//     * @return
//     */
//    private boolean checkPath(UserVo userVo, long goodsId, String path)
//    {
//        if (userVo == null || path == null)
//            return false;
//
//        String oldPath = redisService.get(SkKeyPrefix.SK_PATH, "" + userVo.getUuid() + "_" + goodsId, String.class);
//
//        return path.equals(oldPath);
//    }
//
//    private boolean checkVerifyCode(UserVo userVo, long goodsId, int verifyCode)
//    {
//        if (userVo == null || goodsId <= 0)
//            return false;
//
//        Integer oldCode = redisService.get(SkKeyPrefix.VERIFY_RESULT, "" + userVo.getUuid() + "_" + goodsId, Integer.class);
//        if (oldCode == null || oldCode - verifyCode != 0)
//            return false;
//
//        //核对成功
//        redisService.delete(SkKeyPrefix.VERIFY_RESULT, "" + userVo.getUuid() + "_" + goodsId);
//        return true;
//    }
//
//    @GetMapping("/verifyCode")
//    @ResponseBody
//    public Result<String> getVerifyCode(HttpServletResponse response, UserVo userVo, @RequestParam("goodsId") long goodsId)
//    {
//        log.info("获取验证码");
//        if (userVo == null || goodsId <= 0)
//            return Result.error(CodeMsg.SESSION_ERROR);
//
//        //验证path地址是否存在
//
//        String path = redisService.get(SkKeyPrefix.SK_PATH, "" + userVo.getUuid() + "_" + goodsId, String.class);
//        if (path != null)
//            redisService.delete(SkKeyPrefix.SK_PATH, "" + userVo.getUuid() + "_" + goodsId);
//
//        // 创建验证码
//        try
//        {
//            // String verifyCodeJsonString = seckillService.createVerifyCode(user, goodsId);
//            VerifyCodeVo verifyCode = VerifyCodeUtil.createVerifyCode();
//
//            // 验证码结果预先存到redis中
//            redisService.set(SkKeyPrefix.VERIFY_RESULT, userVo.getUuid() + "_" + goodsId, verifyCode.getExpResult());
//            ServletOutputStream out = response.getOutputStream();
//            // 将图片写入到resp对象中
//            ImageIO.write(verifyCode.getImage(), "JPEG", out);
//            out.close();
//            out.flush();
//
//            return null;
//
//        } catch (Exception e)
//        {
//            e.printStackTrace();
//            return Result.error(CodeMsg.SECKILL_FAIL);
//        }
//    }
//
//
//    //服务器启动时候加载商品列表信息
//    @Override
//    public void afterPropertiesSet() throws Exception
//    {
//        List<GoodsVo> goodsVoList = goodsService.listGoodsVo();
//        if (goodsVoList == null)
//            return;
//
//        for (GoodsVo goodsVo : goodsVoList)
//        {
//            redisService.set(GoodsKeyPrefix.GOODS_STOCK, "" + goodsVo.getId(), goodsVo.getStockCount());
//            localOverMap.put(goodsVo.getId(), false);
//        }
//    }
//}
