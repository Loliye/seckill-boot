package com.seckill.gateway.controller;

import com.seckill.common.api.goods.GoodsServiceApi;
import com.seckill.common.api.goods.vo.GoodsVo;
import com.seckill.common.api.order.OrderServiceApi;
import com.seckill.common.api.order.vo.OrderDetailVo;
import com.seckill.common.api.user.vo.UserVo;
import com.seckill.common.domain.OrderInfo;
import com.seckill.common.result.CodeMsg;
import com.seckill.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/order")
@Slf4j
public class OrderController
{
    @Reference(interfaceClass = OrderServiceApi.class)
    OrderServiceApi orderService;

    @Reference(interfaceClass = GoodsServiceApi.class)
    GoodsServiceApi goodsService;

    @RequestMapping("/detail")
    @ResponseBody
    public Result<OrderDetailVo> orderInfo(Model model, UserVo userVo, @RequestParam("orderId") long orderId)
    {
        log.info("获取商品id:{}的详细信息",orderId);

        if(userVo==null)
            return Result.error(CodeMsg.SESSION_ERROR);

        OrderInfo orderInfo=orderService.getOrderById(orderId);

        if(orderInfo==null)
            return Result.error(CodeMsg.ORDER_NOT_EXIST);

        long goodsId=orderInfo.getGoodsId();
        GoodsVo goodsVo=goodsService.getGoodsVoByGoodsId(goodsId);

        OrderDetailVo detailVo=new OrderDetailVo();
        detailVo.setGoods(goodsVo);
        detailVo.setUser(userVo);
        detailVo.setOrder(orderInfo);

        return Result.success(detailVo);
    }
}
