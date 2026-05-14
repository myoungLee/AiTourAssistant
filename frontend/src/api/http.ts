/*
 * @author myoung
 */
import axios from 'axios'

/**
 * 统一后端 HTTP 客户端，后续认证阶段会在这里注入 JWT。
 *
 * @author myoung
 */
export const http = axios.create({
  baseURL: '/api',
  timeout: 15000
})
