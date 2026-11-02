# Intent Brief

## Original Intent

把我的 Gemtronic 智能戒指的 3D 渲染放在 PICO 里展示：戒指出现在我手上，跟着手走，并且能倾斜手指控制切换戒指屏幕上的页面。戒指模型文件 C:/Users/linzh/Desktop/IDE/aspirium web/assets/model/ring.glb；屏幕页面是固件画面图片，在 C:/Users/linzh/Desktop/IDE/aspirium-web-gesture/assets/ui/。这一版只验证核心：戒指跟手，倾斜能换 3 张图。

## Product Goal

Let the wearer see the Gemtronic smart ring on their own hand in PICO and flip through its screen pages by tilting the finger.

## Core Capabilities

- Show the Gemtronic ring 3D model on the user's hand.
- Keep the ring following the hand as it moves.
- Show firmware screen images on the ring's screen.
- Tilting the finger switches between 3 screen images.

## User Flow

The user opens the app and raises their hand. The ring appears on the hand and follows it. The user tilts the finger, the ring's screen changes to the next image, and tilting again continues through the 3 images.

## Application ID

- ID: `com.example.gemtronicring.p4e7a91c3`
- Source: `generated_default`

## Assumptions

- The 3 images are chosen from the firmware image folder.
- When no hand is visible, the ring is not shown.
