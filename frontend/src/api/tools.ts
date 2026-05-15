/*
 * @author myoung
 */
import { http } from './http'
import type { ToolStatusResponse } from './types'

/**
 * 工具状态接口封装。
 *
 * @author myoung
 */
export const toolsApi = {
  /**
   * 查询当前 MCP 工具模式和工具列表。
   */
  async status(): Promise<ToolStatusResponse> {
    const response = await http.get<ToolStatusResponse>('/tools/status')
    return response.data
  }
}
