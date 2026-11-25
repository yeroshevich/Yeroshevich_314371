import type { ThemeConfig } from 'antd';
import { colors, controlHeight, fontFamily, radius, shadowCard, typeScale } from './tokens';

/**
 * Тема Ant Design 5, собранная из токенов системы дизайна.
 * Подключается один раз в <ConfigProvider theme={antdTheme}> (src/App.tsx);
 * все компоненты Ant Design получают цвета, шрифт, скругления и высоты отсюда.
 */
export const antdTheme: ThemeConfig = {
  token: {
    colorPrimary: colors.colorPrimary,
    colorPrimaryHover: colors.colorPrimaryHover,
    colorSuccess: colors.colorSuccess,
    // Автоматическая палитра Ant Design даёт для тёмно-зелёного серо-болотный фон;
    // берём сам colorSuccess с прозрачностью — остаёмся в токенах, фон чище.
    colorSuccessBg: 'rgba(47,158,68,.08)',
    colorSuccessBgHover: 'rgba(47,158,68,.14)',
    colorSuccessBorder: 'rgba(47,158,68,.35)',
    colorError: colors.colorError,
    colorWarning: colors.colorWarning,
    colorInfo: colors.colorInfo,
    colorLink: colors.colorPrimary,
    colorLinkHover: colors.colorPrimaryHover,
    colorText: colors.colorText,
    colorTextSecondary: colors.colorTextSecondary,
    colorTextHeading: colors.colorText,
    colorBorder: colors.colorBorder,
    colorBorderSecondary: colors.colorBorder,
    colorBgLayout: colors.colorBgLayout,
    colorBgContainer: colors.colorBgContainer,

    fontFamily,
    fontSize: typeScale.body.fontSize,
    lineHeight: typeScale.body.lineHeight / typeScale.body.fontSize,
    fontSizeSM: typeScale.small.fontSize,
    fontSizeHeading1: typeScale.h1.fontSize,
    fontSizeHeading2: typeScale.h2.fontSize,
    fontSizeHeading3: typeScale.h3.fontSize,
    lineHeightHeading1: typeScale.h1.lineHeight / typeScale.h1.fontSize,
    lineHeightHeading2: typeScale.h2.lineHeight / typeScale.h2.fontSize,
    lineHeightHeading3: typeScale.h3.lineHeight / typeScale.h3.fontSize,
    fontWeightStrong: 600,

    borderRadius: radius.control,
    borderRadiusLG: radius.card,
    borderRadiusSM: 6,
    controlHeight: controlHeight.md,
    controlHeightLG: controlHeight.lg,
    controlHeightSM: controlHeight.sm,
    boxShadowTertiary: shadowCard,

    padding: 16,
    paddingLG: 24,
    paddingSM: 12,
    paddingXS: 8,
    margin: 16,
    marginLG: 24,
    marginXL: 32,
    marginXXL: 48,
  },
  components: {
    Layout: {
      siderBg: colors.siderBg,
      triggerBg: colors.siderBg,
      headerBg: colors.colorBgContainer,
      headerHeight: 64,
      headerPadding: '0 24px',
      bodyBg: colors.colorBgLayout,
    },
    Menu: {
      darkItemBg: colors.siderBg,
      darkSubMenuItemBg: colors.siderBg,
      darkPopupBg: colors.siderBg,
      darkItemSelectedBg: colors.colorPrimary,
      itemHeight: 44,
      itemBorderRadius: radius.control,
      iconSize: 16,
      collapsedIconSize: 18,
    },
    Card: {
      borderRadiusLG: radius.card,
      headerFontSize: typeScale.h3.fontSize,
    },
    Table: {
      headerBg: colors.colorBgLayout,
      headerColor: colors.colorTextSecondary,
      rowHoverBg: colors.colorBgLayout,
    },
    Statistic: {
      contentFontSize: 24,
    },
    Steps: {
      titleLineHeight: 22,
    },
  },
};
