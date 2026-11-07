// folia colorMix.parseColorChannels 的 shim：支持 sakura 里出现的两种颜色写法——
// CSS 变量的 #rrggbb，和封面取色的 hsl(H S% L%)（utils/palette.ts 的产物）。

export interface ColorChannels {
  r: number;
  g: number;
  b: number;
}

function fromHex(input: string): ColorChannels | null {
  const match = /^#([0-9a-f]{6})$/i.exec(input.trim());
  if (!match || !match[1]) return null;
  const value = Number.parseInt(match[1], 16);
  return { r: (value >> 16) & 255, g: (value >> 8) & 255, b: value & 255 };
}

function fromHsl(input: string): ColorChannels | null {
  const match = /hsl\(\s*([\d.]+)(?:deg)?[\s,]+([\d.]+)%[\s,]+([\d.]+)%\s*\)/i.exec(input.trim());
  if (!match) return null;
  const h = ((Number(match[1]) % 360) + 360) % 360;
  const s = Number(match[2]) / 100;
  const l = Number(match[3]) / 100;
  const a = s * Math.min(l, 1 - l);
  const f = (n: number) => {
    const k = (n + h / 30) % 12;
    return l - a * Math.max(-1, Math.min(k - 3, 9 - k, 1));
  };
  return { r: Math.round(f(0) * 255), g: Math.round(f(8) * 255), b: Math.round(f(4) * 255) };
}

function fromRgb(input: string): ColorChannels | null {
  const match = /rgba?\(\s*([\d.]+)[\s,]+([\d.]+)[\s,]+([\d.]+)/i.exec(input.trim());
  if (!match) return null;
  return { r: Number(match[1]), g: Number(match[2]), b: Number(match[3]) };
}

export const parseColorChannels = (color: string): ColorChannels | null =>
  fromHex(color) ?? fromHsl(color) ?? fromRgb(color);
