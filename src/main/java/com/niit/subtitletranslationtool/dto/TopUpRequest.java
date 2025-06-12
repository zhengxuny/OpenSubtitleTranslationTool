package com.niit.subtitletranslationtool.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;

/**
 * 充值请求的数据传输对象 (DTO).
 *
 * <p>
 *   该类用于封装用户发起充值请求时需要传递的数据，例如充值金额。
 *   使用 Lombok 注解简化了代码，自动生成 getter、setter、构造器等方法。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopUpRequest {
    /**
     * 充值金额.
     *
     * <p>
     *   用户希望充值的金额，使用 BigDecimal 类型以保证精度，避免浮点数计算误差。
     * </p>
     */
    private BigDecimal amount;
}