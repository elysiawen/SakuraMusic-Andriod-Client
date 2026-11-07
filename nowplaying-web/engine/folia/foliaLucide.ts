/**
 * folia lucideIconResolver 的 shim。sakura 不带 lucide 图标库：
 * 句尾涂鸦图标不画、emoji 歌词退回字体骨架（nebbiaGlyphs 对 null 已有回落），其余不受影响。
 */

/** lucide 图标节点（lucide-react 的 IconNode）：[标签名, 属性] 列表。 */
export type IconNode = Array<[string, Record<string, unknown>]>;

export const loadLucideIcons = async (_names: readonly unknown[]): Promise<void> => {};

export const resolveLucideIconNames = (_names: readonly string[] | undefined): string[] => [];

export const resolveLucideIconNode = (
  _name: string | null | undefined,
): Array<[string, Record<string, unknown>]> | null => null;
