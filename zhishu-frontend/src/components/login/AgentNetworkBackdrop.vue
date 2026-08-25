<template>
  <div class="agent-network" :class="`agent-network--${variant}`" aria-hidden="true">
    <div class="agent-network__wash" />
    <div class="agent-network__hex" />
    <svg class="agent-network__graph" viewBox="0 0 1200 800" preserveAspectRatio="xMidYMid slice">
      <defs>
        <radialGradient :id="hubGlowId" cx="50%" cy="50%" r="50%">
          <stop offset="0%" stop-color="#0969da" stop-opacity="0.28" />
          <stop offset="100%" stop-color="#0969da" stop-opacity="0" />
        </radialGradient>
        <linearGradient :id="edgeId" x1="0%" y1="0%" x2="100%" y2="0%">
          <stop offset="0%" stop-color="#0969da" stop-opacity="0.12" />
          <stop offset="50%" stop-color="#0969da" stop-opacity="0.55" />
          <stop offset="100%" stop-color="#1a7f37" stop-opacity="0.18" />
        </linearGradient>
      </defs>

      <circle cx="380" cy="390" r="210" :fill="`url(#${hubGlowId})`" />

      <g class="edges" :stroke="`url(#${edgeId})`" fill="none" stroke-linecap="round">
        <path
          v-for="edge in edges"
          :key="edge.id"
          :d="edge.d"
          class="edge"
          :stroke-width="edge.hub ? 1.6 : 1.1"
        />
      </g>

      <g class="packets">
        <circle
          v-for="packet in packets"
          :key="packet.id"
          r="2.4"
          class="packet"
          :style="{ animationDuration: packet.duration, animationDelay: packet.delay }"
        >
          <animateMotion
            :dur="packet.duration"
            :begin="packet.delay"
            repeatCount="indefinite"
            rotate="auto"
            :path="packet.d"
          />
        </circle>
      </g>

      <g
        v-for="node in nodes"
        :key="node.id"
        class="node"
        :class="`node--${node.kind}`"
        :transform="`translate(${node.x}, ${node.y})`"
      >
        <circle v-if="node.kind === 'hub'" class="node__halo" r="28" />
        <polygon v-if="node.kind === 'agent'" class="node__shape" points="0,-11 10,-6 10,6 0,11 -10,6 -10,-6" />
        <rect v-else-if="node.kind === 'mcp'" class="node__shape" x="-8" y="-8" width="16" height="16" rx="2" transform="rotate(45)" />
        <circle v-else-if="node.kind === 'knowledge'" class="node__shape" r="8" />
        <rect v-else-if="node.kind === 'system'" class="node__shape" x="-7.5" y="-7.5" width="15" height="15" rx="3" />
        <polygon v-else-if="node.kind === 'hub'" class="node__shape node__shape--hub" points="0,-14 12,-7 12,7 0,14 -12,7 -12,-7" />
        <text v-if="node.label" class="node__label" y="26">{{ node.label }}</text>
      </g>
    </svg>
    <div class="agent-network__veil" />
  </div>
</template>

<script setup lang="ts">
import { useId } from 'vue'

defineOptions({ name: 'AgentNetworkBackdrop' })

withDefaults(
  defineProps<{
    variant?: 'login' | 'wide'
  }>(),
  { variant: 'login' },
)

const uid = useId().replace(/:/g, '')
const hubGlowId = `hub-glow-${uid}`
const edgeId = `edge-grad-${uid}`

interface GraphNode {
  id: string
  kind: 'hub' | 'agent' | 'mcp' | 'knowledge' | 'system'
  x: number
  y: number
  label?: string
}

const nodes: GraphNode[] = [
  { id: 'hub', kind: 'hub', x: 380, y: 390, label: 'ZSIF' },
  { id: 'agent-a', kind: 'agent', x: 250, y: 250, label: 'Agent' },
  { id: 'agent-b', kind: 'agent', x: 520, y: 250, label: 'Agent' },
  { id: 'agent-c', kind: 'agent', x: 230, y: 520, label: 'Agent' },
  { id: 'mcp-1', kind: 'mcp', x: 620, y: 390, label: 'MCP' },
  { id: 'mcp-2', kind: 'mcp', x: 560, y: 540, label: 'MCP' },
  { id: 'kg', kind: 'knowledge', x: 380, y: 560, label: 'RAG' },
  { id: 'sso', kind: 'system', x: 200, y: 380, label: 'SSO' },
  { id: 'ops', kind: 'system', x: 480, y: 160, label: 'Ops' },
]

function curve(from: GraphNode, to: GraphNode): string {
  const mx = (from.x + to.x) / 2
  const my = (from.y + to.y) / 2
  const dx = to.y - from.y
  const dy = from.x - to.x
  const len = Math.hypot(dx, dy) || 1
  const bow = 28
  const cx = mx + (dx / len) * bow
  const cy = my + (dy / len) * bow
  return `M ${from.x} ${from.y} Q ${cx} ${cy} ${to.x} ${to.y}`
}

const links: Array<[string, string]> = [
  ['hub', 'agent-a'],
  ['hub', 'agent-b'],
  ['hub', 'agent-c'],
  ['hub', 'mcp-1'],
  ['hub', 'mcp-2'],
  ['hub', 'kg'],
  ['hub', 'sso'],
  ['hub', 'ops'],
  ['agent-a', 'sso'],
  ['agent-b', 'mcp-1'],
  ['agent-c', 'kg'],
  ['mcp-1', 'mcp-2'],
  ['agent-b', 'ops'],
]

const nodeMap = Object.fromEntries(nodes.map((n) => [n.id, n]))

const edges = links.map(([a, b], i) => {
  const from = nodeMap[a]!
  const to = nodeMap[b]!
  return {
    id: `${a}-${b}`,
    d: curve(from, to),
    hub: a === 'hub' || b === 'hub',
    i,
  }
})

const packets = edges.slice(0, 8).map((edge, i) => ({
  id: `p-${edge.id}`,
  d: edge.d,
  duration: `${5.5 + (i % 4) * 0.8}s`,
  delay: `${-i * 0.7}s`,
}))
</script>

<style scoped lang="scss">
.agent-network {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}

.agent-network__wash {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(900px 520px at 28% 42%, rgba(9, 105, 218, 0.1), transparent 58%),
    radial-gradient(700px 420px at 78% 18%, rgba(26, 127, 55, 0.06), transparent 62%),
    linear-gradient(180deg, #eef3f8 0%, #f7f8fa 46%, #ffffff 100%);
}

.agent-network__hex {
  position: absolute;
  inset: 0;
  opacity: 0.35;
  background-image:
    linear-gradient(30deg, rgba(9, 105, 218, 0.07) 1px, transparent 1px),
    linear-gradient(150deg, rgba(9, 105, 218, 0.07) 1px, transparent 1px),
    linear-gradient(90deg, rgba(9, 105, 218, 0.04) 1px, transparent 1px);
  background-size: 56px 96px, 56px 96px, 28px 28px;
  mask-image: radial-gradient(ellipse 70% 70% at 32% 46%, #000 20%, transparent 78%);
}

.agent-network__graph {
  position: absolute;
  inset: -6% 0 -8% -8%;
  width: 78%;
  height: 118%;
  opacity: 0.92;
}

.agent-network__veil {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    90deg,
    rgba(238, 243, 248, 0.35) 0%,
    transparent 36%,
    rgba(255, 255, 255, 0.28) 62%,
    rgba(255, 255, 255, 0.82) 82%,
    #ffffff 100%
  );
}

.agent-network--wide {
  .agent-network__wash {
    background:
      radial-gradient(900px 520px at 78% 36%, rgba(9, 105, 218, 0.12), transparent 60%),
      radial-gradient(640px 380px at 92% 12%, rgba(26, 127, 55, 0.07), transparent 64%),
      transparent;
  }

  .agent-network__hex {
    opacity: 0.3;
    mask-image: radial-gradient(ellipse 68% 72% at 78% 40%, #000 16%, transparent 78%);
  }

  .agent-network__graph {
    top: -12%;
    right: -8%;
    left: 42%;
    width: auto;
    height: 118%;
    opacity: 0.9;
  }

  .agent-network__veil {
    background: linear-gradient(
      90deg,
      #ffffff 0%,
      rgba(255, 255, 255, 0.92) 22%,
      rgba(255, 255, 255, 0.4) 46%,
      transparent 68%
    );
  }
}

.edge {
  stroke-dasharray: 5 9;
  animation: edge-flow 18s linear infinite;
}

.packet {
  fill: #0969da;
  filter: drop-shadow(0 0 4px rgba(9, 105, 218, 0.65));
}

.node__halo {
  fill: rgba(9, 105, 218, 0.08);
  stroke: rgba(9, 105, 218, 0.18);
  stroke-width: 1;
  transform-box: fill-box;
  transform-origin: center;
  animation: hub-pulse 4.8s ease-in-out infinite;
}

.node__shape {
  fill: #ffffff;
  stroke: #0969da;
  stroke-width: 1.4;

  &--hub {
    fill: #0969da;
    stroke: #0550ae;
  }
}

.node--mcp .node__shape {
  stroke: #1a7f37;
}

.node--knowledge .node__shape {
  stroke: #8250df;
}

.node--system .node__shape {
  stroke: #656d76;
}

.node--agent .node__shape {
  fill: #f0f6ff;
}

.node__label {
  fill: #656d76;
  font-size: 11px;
  font-family: Outfit, 'Noto Sans SC', sans-serif;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-anchor: middle;
}

@keyframes edge-flow {
  to {
    stroke-dashoffset: -140;
  }
}

@keyframes hub-pulse {
  0%,
  100% {
    transform: scale(1);
    opacity: 0.85;
  }
  50% {
    transform: scale(1.12);
    opacity: 1;
  }
}

@media (prefers-reduced-motion: reduce) {
  .edge,
  .packet,
  .node__halo {
    animation: none;
  }
}

@media (max-width: 1024px) {
  .agent-network__graph {
    width: 120%;
    opacity: 0.45;
  }

  .agent-network--wide .agent-network__graph {
    inset: -8% -18% auto auto;
    width: 88%;
    opacity: 0.4;
  }

  .node__label {
    display: none;
  }
}
</style>
