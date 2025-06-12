package com.niit.subtitletranslationtool.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户登录请求数据传输对象。
 *
 * <p>
 *   该类用于封装客户端发送的登录请求，包含用户名和密码。
 *   使用 Lombok 注解自动生成 Getter、Setter、toString 等方法，简化代码。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
  /** 用户名 */
  private String username;

  /** 密码 */
  private String password;
}