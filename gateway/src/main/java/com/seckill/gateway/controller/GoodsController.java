package com.seckill.gateway.controller;

import com.seckill.common.api.cache.RedisServiceApi;
import com.seckill.common.api.cache.vo.GoodsKeyPrefix;
import com.seckill.common.api.goods.GoodsServiceApi;
import com.seckill.common.api.goods.vo.GoodsDetailVo;
import com.seckill.common.api.goods.vo.GoodsVo;
import com.seckill.common.api.user.vo.UserVo;
import com.seckill.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring5.view.ThymeleafViewResolver;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

@Controller
@Slf4j
@RequestMapping("/goods")
public class GoodsController
{
    @Reference(interfaceClass = RedisServiceApi.class)
    RedisServiceApi redisService;

    @Reference(interfaceClass = GoodsServiceApi.class)
    GoodsServiceApi goodsService;

    /**
     * redis中不存在页面缓存  添加一个视图解析器，自定义手动渲染
     */
    @Autowired
    ThymeleafViewResolver thymeleafViewResolver;

    @RequestMapping(value = "/goodsList", produces = "text/html")
    @ResponseBody
    public String goodsList(HttpServletRequest request, HttpServletResponse response, Model model, UserVo userVo)
    {
        log.info("获取商品列表");

        String html = redisService.get(GoodsKeyPrefix.GOODS_LIST_HTML, "", String.class);
        if (!StringUtils.isEmpty(html))
            return html;

        //redis中没有该页面的缓存  手动渲染
        //查询商品列表  渲染页面

        List<GoodsVo> goodsVoList = goodsService.listGoodsVo();
        model.addAttribute("goodsList", goodsVoList);
        model.addAttribute("user", userVo);

        //进行渲染html
        WebContext webContext = new WebContext(request, response, request.getServletContext(), response.getLocale(), model.asMap());
        html = thymeleafViewResolver.getTemplateEngine().process("goods_list", webContext);

        if (!StringUtils.isEmpty(html))
            redisService.set(GoodsKeyPrefix.GOODS_LIST_HTML, "", html);

        return html;
    }

    @RequestMapping("/getDetails/{goodsId}")
    @ResponseBody
    public Result<GoodsDetailVo> getDetails(UserVo userVo, @PathVariable("goodsId") long goodsId)
    {
        log.info("获取商品详细信息");

        GoodsVo goodsVo = goodsService.getGoodsVoByGoodsId(goodsId);
        long startDate = goodsVo.getStartDate().getTime();
        long endDate = goodsVo.getEndDate().getTime();
        long now = System.currentTimeMillis();

        // 秒杀状态; 0: 秒杀未开始，1: 秒杀进行中，2: 秒杀已结束
        int skStatus = 0;
        // 秒杀剩余时间
        int remainSeconds = 0;

        if (now < startDate)
        { // 秒杀未开始
            skStatus = 0;
            remainSeconds = (int) ((startDate - now) / 1000);
        } else if (now > endDate)
        { // 秒杀已结束
            skStatus = 2;
            remainSeconds = -1;
        } else
        { // 秒杀进行中
            skStatus = 1;
            remainSeconds = 0;
        }

        // 服务端封装商品数据直接传递给客户端，而不用渲染页面
        GoodsDetailVo goodsDetailVo = new GoodsDetailVo();
        goodsDetailVo.setGoods(goodsVo);
        goodsDetailVo.setUser(userVo);
        goodsDetailVo.setRemainSeconds(remainSeconds);
        goodsDetailVo.setSeckillStatus(skStatus);

        return Result.success(goodsDetailVo);

    }


}
