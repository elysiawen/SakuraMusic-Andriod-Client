// folia renderHints 的 shim：sakura 的行没有渲染提示，渲染结束时间就是行结束时间。
export const getLineRenderEndTime = (line: { endTime: number } | null | undefined): number =>
  line ? line.endTime : Number.NEGATIVE_INFINITY;
