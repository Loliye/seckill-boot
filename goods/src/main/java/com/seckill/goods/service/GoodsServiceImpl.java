package com.seckill.goods.service;

import com.seckill.common.api.goods.GoodsServiceApi;
import com.seckill.common.api.goods.vo.GoodsVo;
import com.seckill.common.domain.SeckillGoods;
import com.seckill.goods.dao.GoodsMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Service(interfaceClass = GoodsServiceApi.class)
@Slf4j
public class GoodsServiceImpl implements GoodsServiceApi
{
    @Autowired
    GoodsMapper goodsMapper;

    @Override
    public List<GoodsVo> listGoodsVo()
    {
        return goodsMapper.listGoodsVo();
    }

    @Override
    public GoodsVo getGoodsVoByGoodsId(long goodsId)
    {
        return goodsMapper.getGoodsVoByGoodsId(goodsId);
    }

    @Override
    public GoodsVo getGoodsVoByGoodsId(Long goodsId)
    {
        return goodsMapper.getGoodsVoByGoodsId(goodsId);
    }

    @Override
    public boolean reduceStock(GoodsVo goods)
    {
        SeckillGoods seckillGoods = new SeckillGoods();

        seckillGoods.setGoodsId(goods.getId());
        Integer ret = goodsMapper.reduceStack(seckillGoods);

        log.info("GoodsServiceImpl 减少GoodsVo:{} 结果：{}",goods,ret);
        return ret > 0;
    }
}
