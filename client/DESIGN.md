# Design System — 智能轻量化企业管理系统

本设计规范以 Notion 的视觉语言为灵感来源，并针对 B 端企业管理后台场景进行适配扩展。字体使用开源字体 Inter (Google Fonts)。

## 1. Visual Theme & Atmosphere

The design system is built on warm neutrals rather than cold grays, creating a distinctly approachable minimalism that feels like quality paper rather than sterile glass. The page canvas is pure white (`#ffffff`) but the text isn't pure black -- it's a warm near-black (`rgba(0,0,0,0.95)`) that softens the reading experience imperceptibly. The warm gray scale (`#f6f5f4`, `#31302e`, `#615d59`, `#a39e98`) carries subtle yellow-brown undertones, giving the interface a tactile, almost analog warmth.

The Inter font is the backbone of the system. At display sizes (64px), it uses aggressive negative letter-spacing (-2.125px), creating headlines that feel compressed and precise. The weight range is broader than typical systems: 400 for body, 500 for UI elements, 600 for semi-bold labels, and 700 for display headings. OpenType features `"lnum"` (lining numerals) and `"locl"` (localized forms) are enabled on larger text, adding typographic sophistication that rewards close reading.

The border philosophy favors ultra-thin `1px solid rgba(0,0,0,0.1)` borders -- borders that exist as whispers, barely perceptible division lines that create structure without weight. The shadow system is equally restrained: multi-layer stacks with cumulative opacity never exceeding 0.05, creating depth that's felt rather than seen.

**Key Characteristics:**

- Inter font (Google Fonts, open-source) with negative letter-spacing at display sizes (-2.125px at 64px)
- Warm neutral palette: grays carry yellow-brown undertones (`#f6f5f4` warm white, `#31302e` warm dark)
- Near-black text via `rgba(0,0,0,0.95)` -- not pure black, creating micro-warmth
- Ultra-thin borders: `1px solid rgba(0,0,0,0.1)` throughout -- whisper-weight division
- Multi-layer shadow stacks with sub-0.05 opacity for barely-there depth
- Notion Blue (`#0075de`) as the singular accent color for CTAs and interactive elements
- Pill badges (9999px radius) with tinted blue backgrounds for status indicators
- 8px base spacing unit with an organic, non-rigid scale

## 2. Color Palette & Roles

### Primary

- **Notion Black** (`rgba(0,0,0,0.95)` / `#000000f2`): Primary text, headings, body copy. The 95% opacity softens pure black without sacrificing readability.
- **Pure White** (`#ffffff`): Page background, card surfaces, button text on blue.
- **Notion Blue** (`#0075de`): Primary CTA, link color, interactive accent -- the only saturated color in the core UI chrome.

### Brand Secondary

- **Deep Navy** (`#213183`): Secondary brand color, used sparingly for emphasis and dark feature sections.
- **Active Blue** (`#005bab`): Button active/pressed state -- darker variant of Notion Blue.

### Warm Neutral Scale

- **Warm White** (`#f6f5f4`): Background surface tint, section alternation, subtle card fill. The yellow undertone is key.
- **Warm Dark** (`#31302e`): Dark surface background, dark section text. Warmer than standard grays.
- **Warm Gray 500** (`#615d59`): Secondary text, descriptions, muted labels.
- **Warm Gray 300** (`#a39e98`): Placeholder text, disabled states, caption text.

### Semantic Accent Colors

- **Teal** (`#2a9d99`): Success states, positive indicators.
- **Green** (`#1aae39`): Confirmation, completion badges.
- **Orange** (`#dd5b00`): Warning states, attention indicators.
- **Red** (`#e03e3e`): Danger, destructive actions, critical alerts.
- **Pink** (`#ff64c8`): Decorative accent, feature highlights.
- **Purple** (`#391c57`): Premium features, deep accents.
- **Brown** (`#523410`): Earthy accent, warm feature sections.

### Business Semantic Colors (Enterprise-Specific)

- **Income / Profit** (`#2a9d99` Teal): Revenue numbers, positive financial indicators, profit badges.
- **Expense / Loss** (`#e03e3e` Red): Cost numbers, negative financial indicators, loss badges.
- **Tax Overdue / Warning** (`#dd5b00` Orange): Unpaid tax alerts, overdue deadlines, at-risk indicators.
- **Tax Paid / Neutral** (`#a39e98` Warm Gray 300): Completed/settled items, historical data, inactive states.
- **Tax Exempt / Safe** (`#1aae39` Green): Zero-declaration, exempt items, healthy indicators.

### Interactive

- **Link Blue** (`#0075de`): Primary link color with underline-on-hover.
- **Link Light Blue** (`#62aef0`): Lighter link variant for dark backgrounds.
- **Focus Blue** (`#097fe8`): Focus ring on interactive elements.
- **Badge Blue Bg** (`#f2f9ff`): Pill badge background, tinted blue surface.
- **Badge Blue Text** (`#097fe8`): Pill badge text, darker blue for readability.

### Shadows & Depth

- **Card Shadow** (`rgba(0,0,0,0.04) 0px 4px 18px, rgba(0,0,0,0.027) 0px 2.025px 7.84688px, rgba(0,0,0,0.02) 0px 0.8px 2.925px, rgba(0,0,0,0.01) 0px 0.175px 1.04062px`): Multi-layer card elevation.
- **Deep Shadow** (`rgba(0,0,0,0.01) 0px 1px 3px, rgba(0,0,0,0.02) 0px 3px 7px, rgba(0,0,0,0.02) 0px 7px 15px, rgba(0,0,0,0.04) 0px 14px 28px, rgba(0,0,0,0.05) 0px 23px 52px`): Five-layer deep elevation for modals and featured content.
- **Whisper Border** (`1px solid rgba(0,0,0,0.1)`): Standard division border -- cards, dividers, sections.

## 3. Typography Rules

### Font Family

- **Primary**: `Inter, -apple-system, system-ui, "Segoe UI", Helvetica, "Apple Color Emoji", Arial, "Segoe UI Emoji", "Segoe UI Symbol"` (通过 Google Fonts 引入 Inter 400/500/600/700)
- **OpenType Features**: `"lnum"` (lining numerals) and `"locl"` (localized forms) enabled on display and heading text.

### Hierarchy

| Role              | Font        | Size           | Weight | Line Height  | Letter Spacing | Notes                                    |
| :---------------- | :---------- | :------------- | :----- | :----------- | :------------- | :--------------------------------------- |
| Display Hero      | Inter | 64px (4.00rem) | 700    | 1.00 (tight) | -2.125px       | Maximum compression, billboard headlines |
| Display Secondary | Inter | 54px (3.38rem) | 700    | 1.04 (tight) | -1.875px       | Secondary hero, feature headlines        |
| Section Heading   | Inter | 48px (3.00rem) | 700    | 1.00 (tight) | -1.5px         | Feature section titles, with `"lnum"`    |
| Sub-heading Large | Inter | 40px (2.50rem) | 700    | 1.50         | normal         | Card headings, feature sub-sections      |
| Sub-heading       | Inter | 26px (1.63rem) | 700    | 1.23 (tight) | -0.625px       | Section sub-titles, content headers      |
| Card Title        | Inter | 22px (1.38rem) | 700    | 1.27 (tight) | -0.25px        | Feature cards, list titles               |
| Body Large        | Inter | 20px (1.25rem) | 600    | 1.40         | -0.125px       | Introductions, feature descriptions      |
| Body              | Inter | 16px (1.00rem) | 400    | 1.50         | normal         | Standard reading text                    |
| Body Medium       | Inter | 16px (1.00rem) | 500    | 1.50         | normal         | Navigation, emphasized UI text           |
| Body Semibold     | Inter | 16px (1.00rem) | 600    | 1.50         | normal         | Strong labels, active states             |
| Body Bold         | Inter | 16px (1.00rem) | 700    | 1.50         | normal         | Headlines at body size                   |
| Nav / Button      | Inter | 15px (0.94rem) | 600    | 1.33         | normal         | Navigation links, button text            |
| Caption           | Inter | 14px (0.88rem) | 500    | 1.43         | normal         | Metadata, secondary labels               |
| Caption Light     | Inter | 14px (0.88rem) | 400    | 1.43         | normal         | Body captions, descriptions              |
| Badge             | Inter | 12px (0.75rem) | 600    | 1.33         | 0.125px        | Pill badges, tags, status labels         |
| Micro Label       | Inter | 12px (0.75rem) | 400    | 1.33         | 0.125px        | Small metadata, timestamps               |

### Principles

- **Compression at scale**: Inter at display sizes uses -2.125px letter-spacing at 64px, progressively relaxing to -0.625px at 26px and normal at 16px. The compression creates density at headlines while maintaining readability at body sizes.
- **Four-weight system**: 400 (body/reading), 500 (UI/interactive), 600 (emphasis/navigation), 700 (headings/display). The broader weight range compared to most systems allows nuanced hierarchy.
- **Warm scaling**: Line height tightens as size increases -- 1.50 at body (16px), 1.23-1.27 at sub-headings, 1.00-1.04 at display. This creates denser, more impactful headlines.
- **Badge micro-tracking**: The 12px badge text uses positive letter-spacing (0.125px) -- the only positive tracking in the system, creating wider, more legible small text.

## 4. Component Stylings

### Buttons

**Primary Blue**

- Background: `#0075de` (Notion Blue)
- Text: `#ffffff`
- Padding: 8px 16px
- Radius: 4px (subtle)
- Border: `1px solid transparent`
- Hover: background darkens to `#005bab`
- Active: scale(0.9) transform
- Focus: `2px solid` focus outline, `var(--shadow-level-200)` shadow
- Use: Primary CTA ("Get Notion free", "Try it")

**Secondary / Tertiary**

- Background: `rgba(0,0,0,0.05)` (translucent warm gray)
- Text: `#000000` (near-black)
- Padding: 8px 16px
- Radius: 4px
- Hover: text color shifts, scale(1.05)
- Active: scale(0.9) transform
- Use: Secondary actions, form submissions

**Ghost / Link Button**

- Background: transparent
- Text: `rgba(0,0,0,0.95)`
- Decoration: underline on hover
- Use: Tertiary actions, inline links

**Pill Badge Button**

- Background: `#f2f9ff` (tinted blue)
- Text: `#097fe8`
- Padding: 4px 8px
- Radius: 9999px (full pill)
- Font: 12px weight 600
- Use: Status badges, feature labels, "New" tags

### Cards & Containers

- Background: `#ffffff`
- Border: `1px solid rgba(0,0,0,0.1)` (whisper border)
- Radius: 12px (standard cards), 16px (featured/hero cards)
- Shadow: `rgba(0,0,0,0.04) 0px 4px 18px, rgba(0,0,0,0.027) 0px 2.025px 7.84688px, rgba(0,0,0,0.02) 0px 0.8px 2.925px, rgba(0,0,0,0.01) 0px 0.175px 1.04062px`
- Hover: subtle shadow intensification
- Image cards: 12px top radius, image fills top half

### Inputs & Forms

- Background: `#ffffff`
- Text: `rgba(0,0,0,0.9)`
- Border: `1px solid #dddddd`
- Padding: 6px
- Radius: 4px
- Focus: blue outline ring
- Placeholder: warm gray `#a39e98`

### Navigation

- Clean horizontal nav on white, not sticky
- Brand logo left-aligned (33x34px icon + wordmark)
- Links: Inter 15px weight 500-600, near-black text
- Hover: color shift to `var(--color-link-primary-text-hover)`
- CTA: blue pill button ("Get Notion free") right-aligned
- Mobile: hamburger menu collapse
- Product dropdowns with multi-level categorized menus

### Image Treatment

- Product screenshots with `1px solid rgba(0,0,0,0.1)` border
- Top-rounded images: `12px 12px 0px 0px` radius
- Dashboard/workspace preview screenshots dominate feature sections
- Warm gradient backgrounds behind hero illustrations (decorative character illustrations)

### Distinctive Components

**Feature Cards with Illustrations**

- Large illustrative headers (The Great Wave, product UI screenshots)
- 12px radius card with whisper border
- Title at 22px weight 700, description at 16px weight 400
- Warm white (`#f6f5f4`) background variant for alternating sections

**Trust Bar / Logo Grid**

- Company logos (trusted teams section) in their brand colors
- Horizontal scroll or grid layout with team counts
- Metric display: large number + description pattern

**Metric Cards**

- Large number display (e.g., "$4,200 ROI")
- Inter 40px+ weight 700 for the metric
- Description below in warm gray body text
- Whisper-bordered card container

## 5. Layout Principles

### Spacing System

- Base unit: 8px
- Scale: 2px, 3px, 4px, 5px, 6px, 7px, 8px, 11px, 12px, 14px, 16px, 24px, 32px
- Non-rigid organic scale with fractional values (5.6px, 6.4px) for micro-adjustments

### Grid & Container

- Max content width: approximately 1200px
- Hero: centered single-column with generous top padding (80-120px)
- Feature sections: 2-3 column grids for cards
- Full-width warm white (`#f6f5f4`) section backgrounds for alternation
- Code/dashboard screenshots as contained with whisper border

### Whitespace Philosophy

- **Generous vertical rhythm**: 64-120px between major sections. Notion lets content breathe with vast vertical padding.
- **Warm alternation**: White sections alternate with warm white (`#f6f5f4`) sections, creating gentle visual rhythm without harsh color breaks.
- **Content-first density**: Body text blocks are compact (line-height 1.50) but surrounded by ample margin, creating islands of readable content in a sea of white space.

### Border Radius Scale

- Micro (4px): Buttons, inputs, functional interactive elements
- Subtle (5px): Links, list items, menu items
- Standard (8px): Small cards, containers, inline elements
- Comfortable (12px): Standard cards, feature containers, image tops
- Large (16px): Hero cards, featured content, promotional blocks
- Full Pill (9999px): Badges, pills, status indicators
- Circle (100%): Tab indicators, avatars

## 6. Depth & Elevation

| Level                 | Treatment                                          | Use                                        |
| :-------------------- | :------------------------------------------------- | :----------------------------------------- |
| Flat (Level 0)        | No shadow, no border                               | Page background, text blocks               |
| Whisper (Level 1)     | `1px solid rgba(0,0,0,0.1)`                        | Standard borders, card outlines, dividers  |
| Soft Card (Level 2)   | 4-layer shadow stack (max opacity 0.04)            | Content cards, feature blocks              |
| Deep Card (Level 3)   | 5-layer shadow stack (max opacity 0.05, 52px blur) | Modals, featured panels, hero elements     |
| Focus (Accessibility) | `2px solid var(--focus-color)` outline             | Keyboard focus on all interactive elements |

**Shadow Philosophy**: Notion's shadow system uses multiple layers with extremely low individual opacity (0.01 to 0.05) that accumulate into soft, natural-looking elevation. The 4-layer card shadow spans from 1.04px to 18px blur, creating a gradient of depth rather than a single hard shadow. The 5-layer deep shadow extends to 52px blur at 0.05 opacity, producing ambient occlusion that feels like natural light rather than computer-generated depth. This layered approach makes elements feel embedded in the page rather than floating above it.

### Decorative Depth

- Hero section: decorative character illustrations (playful, hand-drawn style)
- Section alternation: white to warm white (`#f6f5f4`) background shifts
- No hard section borders -- separation comes from background color changes and spacing

## 7. Responsive Behavior

### Breakpoints

| Name          | Width       | Key Changes                          |
| :------------ | :---------- | :----------------------------------- |
| Mobile Small  | <400px      | Tight single column, minimal padding |
| Mobile        | 400-600px   | Standard mobile, stacked layout      |
| Tablet Small  | 600-768px   | 2-column grids begin                 |
| Tablet        | 768-1080px  | Full card grids, expanded padding    |
| Desktop Small | 1080-1200px | Standard desktop layout              |
| Desktop       | 1200-1440px | Full layout, maximum content width   |
| Large Desktop | >1440px     | Centered, generous margins           |

### Touch Targets

- Buttons use comfortable padding (8px-16px vertical)
- Navigation links at 15px with adequate spacing
- Pill badges have 8px horizontal padding for tap targets
- Mobile menu toggle uses standard hamburger button

### Collapsing Strategy

- Hero: 64px display -> scales to 40px -> 26px on mobile, maintains proportional letter-spacing
- Navigation: horizontal links + blue CTA -> hamburger menu
- Feature cards: 3-column -> 2-column -> single column stacked
- Product screenshots: maintain aspect ratio with responsive images
- Trust bar logos: grid -> horizontal scroll on mobile
- Footer: multi-column -> stacked single column
- Section spacing: 80px+ -> 48px on mobile

### Image Behavior

- Workspace screenshots maintain whisper border at all sizes
- Hero illustrations scale proportionally
- Product screenshots use responsive images with consistent border radius
- Full-width warm white sections maintain edge-to-edge treatment

## 8. Accessibility & States

### Focus System

- All interactive elements receive visible focus indicators
- Focus outline: `2px solid` with focus color + shadow level 200
- Tab navigation supported throughout all interactive components
- High contrast text: near-black on white exceeds WCAG AAA (>14:1 ratio)

### Interactive States

- **Default**: Standard appearance with whisper borders
- **Hover**: Color shift on text, scale(1.05) on buttons, underline on links
- **Active/Pressed**: scale(0.9) transform, darker background variant
- **Focus**: Blue outline ring with shadow reinforcement
- **Disabled**: Warm gray (`#a39e98`) text, reduced opacity

### Color Contrast

- Primary text (rgba(0,0,0,0.95)) on white: ~18:1 ratio
- Secondary text (#615d59) on white: ~5.5:1 ratio (WCAG AA)
- Blue CTA (#0075de) on white: ~4.6:1 ratio (WCAG AA for large text)
- Badge text (#097fe8) on badge bg (#f2f9ff): ~4.5:1 ratio (WCAG AA for large text)

## 9. Enterprise Backend Component Specifications

This section extends the base design system with component specifications for the B2B management dashboard, including Element Plus override guidance.

### Element Plus Theme Override Strategy

Override Element Plus CSS variables at `:root` to align with this design system:

```css
:root {
  --el-color-primary: #0075de;
  --el-color-primary-dark-2: #005bab;
  --el-color-success: #2a9d99;
  --el-color-warning: #dd5b00;
  --el-color-danger: #e03e3e;
  --el-color-info: #615d59;
  --el-font-family: 'Inter', -apple-system, system-ui, 'Segoe UI', Helvetica, Arial, sans-serif;
  --el-border-color: rgba(0,0,0,0.1);
  --el-border-color-light: rgba(0,0,0,0.06);
  --el-fill-color-light: #f6f5f4;
  --el-text-color-primary: rgba(0,0,0,0.95);
  --el-text-color-regular: rgba(0,0,0,0.85);
  --el-text-color-secondary: #615d59;
  --el-text-color-placeholder: #a39e98;
  --el-border-radius-base: 4px;
  --el-border-radius-small: 4px;
  --el-border-radius-round: 9999px;
}
```

### Sidebar Navigation

- Width: 220px (collapsed: 64px)
- Background: `#ffffff`
- Right border: `1px solid rgba(0,0,0,0.1)` (whisper border)
- Menu item height: 40px
- Menu item padding: 0 16px
- Menu item text: Inter 14px weight 500, color `rgba(0,0,0,0.65)`
- Menu item hover: background `#f6f5f4`, text color `rgba(0,0,0,0.95)`
- Menu item active: text color `#0075de`, left 3px solid `#0075de` indicator bar, background `#f2f9ff`
- Menu item icon: 20px, 8px gap to text, same color as text
- Section group label: Inter 12px weight 600, color `#a39e98`, letter-spacing 0.5px, uppercase, padding 24px 16px 8px
- Collapse toggle: bottom-aligned, 40px height, whisper border top

### Topbar

- Height: 56px
- Background: `#ffffff`
- Bottom border: `1px solid rgba(0,0,0,0.1)`
- Company name: Inter 15px weight 600, color `rgba(0,0,0,0.95)`
- Company code badge: pill badge style (`#f2f9ff` bg, `#097fe8` text, 12px weight 600, 9999px radius)
- Industry / Taxpayer type: Inter 13px weight 400, color `#615d59`
- Avatar: 32px circle, whisper border, right-aligned

### Data Table

- Header row: background `#f6f5f4`, height 44px, text Inter 13px weight 600, color `#615d59`, sticky (z-index 10)
- Body row: height 48px, text Inter 14px weight 400, color `rgba(0,0,0,0.85)`
- Row hover: background `rgba(0,117,222,0.03)`
- Row selected (checkbox): background `#f2f9ff`
- Zebra striping: disabled (rely on hover for distinction)
- Border: horizontal only, `1px solid rgba(0,0,0,0.06)` between rows
- Checkbox column: width 48px, centered
- Action column: width 120px, right-aligned, icon buttons with 8px gap
- Amount cells: Inter 14px weight 600, right-aligned, tabular-nums (lnum). Income amounts in Teal `#2a9d99`, expense amounts in Red `#e03e3e`.
- Empty state: centered illustration (simple line art), heading Inter 16px weight 600, description Inter 14px weight 400 color `#a39e98`
- Pagination: right-aligned, Inter 13px, compact style

### Action Bar (Above Table)

- Height: 48px, horizontal flex layout, align-items center
- Left group: primary action buttons (`[+新增]` blue, `[Excel 导入]` secondary)
- Right group: `[批量删除]` ghost button, disabled by default, when active: text `#e03e3e`, border `1px solid #e03e3e`
- Template download: text link style, Inter 13px weight 500, color `#0075de`, underline on hover, inline after import button

### Filter Bar

- Background: transparent
- Margin bottom: 16px
- Filters inline with 12px gap, flex wrap
- Select/DatePicker: Element Plus default with theme override, max-width 200px
- Active filter count badge: pill badge on "筛选" label if filters applied

### Drawer (Side Panel for Create/Edit)

- Width: 480px (right-aligned)
- Overlay: `rgba(0,0,0,0.3)`
- Animation: slide-in from right, 300ms ease
- Header: Inter 18px weight 700, close icon right-aligned, bottom whisper border
- Body padding: 24px
- Form label: Inter 14px weight 500, color `rgba(0,0,0,0.85)`, margin-bottom 6px
- Required marker: `*` in `#e03e3e` before label text
- Amount input: Inter 24px weight 700 (large display), right-aligned text
- Amount input on blur: auto-append `.00` if no decimals
- Footer: sticky bottom, padding 16px 24px, whisper border top, right-aligned buttons with 12px gap

### Chart Container (Dashboard Cards)

- Background: `#ffffff`
- Border: `1px solid rgba(0,0,0,0.1)`
- Radius: 12px
- Padding: 24px
- Shadow: card shadow (4-layer stack)
- Title: Inter 16px weight 700, color `rgba(0,0,0,0.95)`, margin-bottom 4px
- Subtitle/period: Inter 13px weight 400, color `#a39e98`, margin-bottom 20px
- Chart area: min-height 280px
- ECharts color palette: `['#0075de', '#2a9d99', '#dd5b00', '#ff64c8', '#391c57', '#523410', '#62aef0', '#1aae39']`

### KPI Metric Cards (Home Dashboard)

- Layout: horizontal grid, 4 cards per row on desktop
- Background: `#ffffff`
- Border: whisper border
- Radius: 12px
- Padding: 20px 24px
- Shadow: card shadow
- Metric label: Inter 13px weight 500, color `#615d59`
- Metric value: Inter 28px weight 700, color `rgba(0,0,0,0.95)`, tabular-nums
- Metric value (warning): color `#e03e3e` when flag is true (e.g., unpaid tax)
- Trend indicator: Inter 12px weight 600, pill badge, Teal for positive / Red for negative

### Toast / Notification

- Position: top-center, 24px from top
- Success: background `#f0faf5`, left 4px solid `#2a9d99`, icon Teal
- Error: background `#fef0f0`, left 4px solid `#e03e3e`, icon Red
- Warning: background `#fef8f0`, left 4px solid `#dd5b00`, icon Orange
- Text: Inter 14px weight 500, color `rgba(0,0,0,0.85)`
- Duration: success auto-dismiss 2s, error requires manual close (X button)
- Radius: 8px, card shadow

### Skeleton Screen

- Background shape: `#f6f5f4` with shimmer animation (left-to-right gradient sweep)
- Shimmer gradient: `linear-gradient(90deg, #f6f5f4 0%, #eeedec 50%, #f6f5f4 100%)`
- Animation: 1.5s ease infinite
- Table skeleton: 8 rows of horizontal bars (height matching row height)
- KPI skeleton: rectangle matching card dimensions
- Chart skeleton: rectangle with rounded corners matching chart container

### AI Chat Interface

**Chat Bubble - User (Right-aligned)**
- Background: `#0075de`
- Text: `#ffffff`, Inter 15px weight 400
- Radius: 16px 16px 4px 16px
- Max-width: 70%
- Padding: 12px 16px

**Chat Bubble - Assistant (Left-aligned)**
- Background: `#f6f5f4`
- Text: `rgba(0,0,0,0.85)`, Inter 15px weight 400
- Radius: 16px 16px 16px 4px
- Max-width: 80%
- Padding: 12px 16px
- Markdown table: whisper border, compact 13px font
- Data source footnote: Inter 12px weight 400, color `#a39e98`, italic, top 8px margin

**HITL Confirmation Card**
- Background: `#ffffff`
- Border: `2px solid #dd5b00` (warning accent, stronger than whisper)
- Radius: 12px
- Shadow: deep shadow (5-layer stack)
- Header: Inter 15px weight 700, icon `⚠️`, text "AI 请求更新企业档案"
- Layout: two-column flex inside card body
- Left column (old value): background `#f6f5f4`, padding 16px, radius 8px, label "当前内容" Inter 12px weight 600 color `#a39e98`
- Right column (new value): background `#f2f9ff`, padding 16px, radius 8px, label "拟更新为" Inter 12px weight 600 color `#097fe8`
- Value text: Inter 14px weight 400
- Footer: 12px gap, right-aligned
- Reject button: ghost style, text `#e03e3e`
- Approve button: primary blue
- Processed state: both buttons disabled, gray text "已处理", no re-click

**Input Console (Bottom-fixed)**
- Background: `#ffffff`
- Top border: `1px solid rgba(0,0,0,0.1)`
- Padding: 12px 24px
- Textarea: min-height 44px, max-height 120px, auto-grow, Inter 15px weight 400
- Send button: 40px circle, `#0075de` background, white arrow icon
- Prompt chips (above input): horizontal scroll, pill badge style, Inter 13px weight 500, click to fill input

**AI Floating Ball (Owner-only)**
- Position: fixed bottom-right, 24px offset
- Size: 48px circle
- Background: `#0075de`
- Icon: white AI/sparkle icon, 24px
- Shadow: deep shadow
- Hover: scale(1.1), transition 200ms
- Click: navigate to full AI chat page

## 10. Agent Prompt Guide

### Quick Color Reference

- Primary CTA: Notion Blue (`#0075de`)
- Background: Pure White (`#ffffff`)
- Alt Background: Warm White (`#f6f5f4`)
- Heading text: Near-Black (`rgba(0,0,0,0.95)`)
- Body text: Near-Black (`rgba(0,0,0,0.95)`)
- Secondary text: Warm Gray 500 (`#615d59`)
- Muted text: Warm Gray 300 (`#a39e98`)
- Border: `1px solid rgba(0,0,0,0.1)`
- Link: Notion Blue (`#0075de`)
- Focus ring: Focus Blue (`#097fe8`)

### Example Component Prompts

- "Create a KPI metric card: white background, whisper border, 12px radius, card shadow. Metric label at 13px Inter weight 500 color #615d59. Metric value at 28px Inter weight 700 color rgba(0,0,0,0.95) with tabular-nums. Teal pill badge for positive trend, red for negative."
- "Design a data table row: height 48px, Inter 14px weight 400. Amount column right-aligned, income in #2a9d99, expense in #e03e3e. Checkbox first column 48px. Action icons last column. Horizontal whisper borders between rows. Header row #f6f5f4 background, sticky."
- "Build an AI chat bubble (assistant): left-aligned, #f6f5f4 background, rgba(0,0,0,0.85) text, Inter 15px weight 400, radius 16px 16px 16px 4px, max-width 80%, 12px 16px padding. Data source footnote in 12px italic #a39e98."
- "Create a HITL confirmation card: white bg, 2px solid #dd5b00 border, 12px radius, deep shadow. Two-column layout: left column #f6f5f4 for old value, right column #f2f9ff for new value. Reject button ghost red, approve button primary blue."
- "Design the sidebar navigation: 220px width, white bg, whisper right border. Menu items 40px height, 14px Inter weight 500. Active item: #0075de text, 3px left blue bar, #f2f9ff background."

### Iteration Guide

1. Always use warm neutrals -- grays carry yellow-brown undertones (#f6f5f4, #31302e, #615d59, #a39e98), never blue-gray
2. Letter-spacing scales with font size: -2.125px at 64px, -1.875px at 54px, -0.625px at 26px, normal at 16px
3. Four weights: 400 (read), 500 (interact), 600 (emphasize), 700 (announce)
4. Borders are whispers: 1px solid rgba(0,0,0,0.1) -- never heavier (exception: HITL card uses 2px warning border)
5. Shadows use 4-5 layers with individual opacity never exceeding 0.05
6. The warm white (#f6f5f4) section background is essential for visual rhythm
7. Pill badges (9999px) for status/tags, 4px radius for buttons and inputs
8. Blue (#0075de) is the only saturated color in core UI -- use it sparingly for CTAs and links
9. Financial amounts must use business semantic colors: Teal (#2a9d99) for income/profit, Red (#e03e3e) for expense/loss
10. Override Element Plus variables at :root to align with this design system -- see Section 9
11. Tables favor horizontal-only borders and sticky headers; avoid zebra striping

