"""Live USB dance-pad viewer for Windows. Run with: python pad_monitor.py"""

import ctypes as ct
from ctypes import wintypes as wt
import json
from pathlib import Path
import sys
import tkinter as tk
from tkinter import ttk


class JoyInfo(ct.Structure):
    _fields_ = [(name, wt.DWORD) for name in (
        'size', 'flags', 'x', 'y', 'z', 'r', 'u', 'v', 'buttons',
        'button_number', 'pov', 'reserved1', 'reserved2')]


class JoyCaps(ct.Structure):
    _fields_ = [('mid', wt.WORD), ('pid', wt.WORD), ('name', wt.WCHAR * 32)] + [
        (name, wt.UINT) for name in (
            'xmin', 'xmax', 'ymin', 'ymax', 'zmin', 'zmax', 'buttons',
            'period_min', 'period_max', 'rmin', 'rmax', 'umin', 'umax',
            'vmin', 'vmax', 'caps', 'max_axes', 'num_axes', 'max_buttons')
    ] + [('registry', wt.WCHAR * 32), ('oem', wt.WCHAR * 260)]


winmm = ct.WinDLL('winmm')
winmm.joyGetPosEx.argtypes = [wt.UINT, ct.POINTER(JoyInfo)]
winmm.joyGetPosEx.restype = wt.UINT
winmm.joyGetDevCapsW.argtypes = [ct.c_size_t, ct.POINTER(JoyCaps), wt.UINT]
winmm.joyGetDevCapsW.restype = wt.UINT


def read_pad(device):
    state = JoyInfo()
    state.size, state.flags = ct.sizeof(state), 255
    return state if winmm.joyGetPosEx(device, ct.byref(state)) == 0 else None


def devices():
    result = []
    for device in range(16):
        caps = JoyCaps()
        if winmm.joyGetDevCapsW(device, ct.byref(caps), ct.sizeof(caps)) == 0:
            if read_pad(device) is not None:
                result.append((device, caps))
    return result


def signals(state, caps):
    result = {f'B{i + 1}' for i in range(32) if state.buttons & (1 << i)}
    for axis in ('x', 'y', 'z', 'r', 'u', 'v'):
        capability = {'z': 1, 'r': 2, 'u': 4, 'v': 8}.get(axis)
        if capability is not None and not caps.caps & capability:
            continue
        low, high = getattr(caps, axis + 'min'), getattr(caps, axis + 'max')
        if high <= low:
            continue
        value = (getattr(state, axis) - low) / (high - low)
        if value < .25:
            result.add(axis.upper() + '-')
        elif value > .75:
            result.add(axis.upper() + '+')
    if state.pov != 65535 and state.pov <= 35999:
        angle = state.pov / 100
        if angle >= 292.5 or angle <= 67.5:
            result.add('POV up')
        if 22.5 <= angle <= 157.5:
            result.add('POV right')
        if 112.5 <= angle <= 247.5:
            result.add('POV down')
        if 202.5 <= angle <= 337.5:
            result.add('POV left')
    return result


DEFAULTS = {
    'SELECT': ['B9'], 'START': ['B10'],
    'CROSS': ['B7'], 'UP': ['B3'], 'CIRCLE': ['B8'],
    'LEFT': ['B1'], 'RIGHT': ['B4'],
    'CENTER': ['Y+', 'POV down'],
    'TRIANGLE': ['B6'], 'DOWN': ['B2'], 'SQUARE': ['B5'],
}
CONFIG = Path(__file__).with_name('pad_mapping.json')


class Monitor(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title('Dance Pad • Live Signals')
        self.geometry('760x830')
        self.minsize(650, 760)
        self.configure(bg='#10141e')
        self.mapping = {key: list(value) for key, value in DEFAULTS.items()}
        try:
            saved = json.loads(CONFIG.read_text())
            for key in self.mapping:
                if isinstance(saved.get(key), list) and all(isinstance(s, str) for s in saved[key]):
                    self.mapping[key] = saved[key]
        except (OSError, ValueError, TypeError, AttributeError):
            pass
        self.available = []
        self.device = None
        self.caps = None
        self.learning = None
        self.previous = set()
        self.active = set()
        self.last_event = 'No presses recorded yet'
        self.items = {}
        self.status = tk.StringVar(value='Searching for controllers…')
        self.hint = tk.StringVar(value='Click an area, release the pad, then press its matching control to map it.')
        self.raw = tk.StringVar()
        self.event = tk.StringVar(value=self.last_event)
        tk.Label(self, text='DANCE PAD', font=('Segoe UI', 22, 'bold'), fg='white', bg='#10141e').pack(pady=(14, 0))
        tk.Label(self, textvariable=self.status, fg='#a9b8ca', bg='#10141e').pack(pady=4)
        toolbar = tk.Frame(self, bg='#10141e')
        toolbar.pack(pady=5)
        self.selector = ttk.Combobox(toolbar, state='readonly', width=53)
        self.selector.pack(side='left', padx=5)
        self.selector.bind('<<ComboboxSelected>>', self.select)
        ttk.Button(toolbar, text='Rescan', command=self.rescan).pack(side='left')
        ttk.Button(toolbar, text='Reset mapping', command=self.reset).pack(side='left', padx=5)
        self.canvas = tk.Canvas(self, width=530, height=475, bg='#10141e', highlightthickness=0)
        self.canvas.pack(pady=4)
        self.draw()
        tk.Label(self, textvariable=self.hint, fg='#ffd877', bg='#10141e', wraplength=710).pack(pady=5)
        tk.Label(self, text='RAW BUTTONS', font=('Segoe UI', 10, 'bold'), fg='#a9b8ca', bg='#10141e').pack(pady=(8, 4))
        row = tk.Frame(self, bg='#10141e')
        row.pack()
        self.button_labels = []
        for i in range(12):
            label = tk.Label(row, text=str(i + 1), width=4, pady=7, bg='#253044', fg='#a9b8ca')
            label.pack(side='left', padx=3)
            self.button_labels.append(label)
        tk.Label(self, textvariable=self.raw, font=('Consolas', 10), fg='#c5d4e6', bg='#10141e', justify='left').pack(pady=10)
        tk.Label(self, textvariable=self.event, fg='#7fe2ae', bg='#10141e', wraplength=710).pack()
        self.bind('<Escape>', lambda _e: self.cancel_mapping())
        self.rescan()
        self.after(16, self.poll)

    def draw(self):
        c = self.canvas
        c.create_rectangle(6, 6, 524, 469, fill='#080b11', outline='#43536a', width=3)
        layout = [
            ('SELECT', 'SELECT', 22, 20, 250, 69, '#e50083'),
            ('START', 'START', 280, 20, 508, 69, '#09bde8'),
            ('CROSS', '×', 22, 85, 178, 204, '#8e9bab'),
            ('UP', '↑', 187, 85, 343, 204, '#09bde8'),
            ('CIRCLE', '○', 352, 85, 508, 204, '#8e9bab'),
            ('LEFT', '←', 22, 213, 178, 332, '#e50083'),
            ('RIGHT', '→', 352, 213, 508, 332, '#e50083'),
            ('TRIANGLE', '△', 22, 341, 178, 460, '#8e9bab'),
            ('DOWN', '↓', 187, 341, 343, 460, '#09bde8'),
            ('SQUARE', '□', 352, 341, 508, 460, '#8e9bab'),
        ]
        center = c.create_oval(191, 216, 339, 329, fill='#182232', outline='#43536a', width=3, tags='pad_CENTER')
        c.create_text(265, 251, text='Stay Cool!', fill='white', font=('Segoe UI', 17, 'bold'), tags='pad_CENTER')
        c.create_text(265, 277, text='CENTER', fill='#a9b8ca', font=('Segoe UI', 9), tags='pad_CENTER')
        center_binding = c.create_text(265, 305, text='', fill='#a9b8ca', font=('Segoe UI', 8), tags='pad_CENTER')
        self.items['CENTER'] = (center, center_binding, '#43536a')
        c.tag_bind('pad_CENTER', '<Button-1>', lambda _e: self.learn('CENTER'))
        for name, symbol, x1, y1, x2, y2, color in layout:
            tag = 'pad_' + name
            rect = c.create_rectangle(x1, y1, x2, y2, fill='#182232', outline=color, width=3, tags=tag)
            c.create_text((x1+x2)/2, (y1+y2)/2 - (8 if y2-y1 > 60 else 3), text=symbol,
                          fill='white', font=('Segoe UI Symbol', 48 if y2-y1 > 60 else 12, 'bold'), tags=tag)
            binding = c.create_text((x1+x2)/2, y2-13, text='', fill='#a9b8ca', font=('Segoe UI', 8), tags=tag)
            self.items[name] = (rect, binding, color)
            c.tag_bind(tag, '<Button-1>', lambda _e, n=name: self.learn(n))
        self.paint()

    def paint(self):
        for name, (rect, binding, color) in self.items.items():
            pressed = bool(self.active.intersection(self.mapping[name]))
            self.canvas.itemconfigure(rect, fill='#167d56' if pressed else '#182232',
                                      outline='#ffe478' if self.learning == name else ('#70ffbd' if pressed else color),
                                      width=5 if pressed or self.learning == name else 3)
            self.canvas.itemconfigure(binding, text=' + '.join(self.mapping[name]) or 'unmapped')

    def rescan(self):
        self.available = devices()
        self.selector['values'] = [f'{d}: {c.name}  [{c.mid:04X}:{c.pid:04X}] • {c.buttons} buttons' for d, c in self.available]
        if self.available:
            index = next((i for i, (_, c) in enumerate(self.available) if (c.mid, c.pid) == (0x79, 6)), 0)
            self.selector.current(index)
            self.select()
        else:
            self.device = None
            self.selector.set('No controller detected')
            self.status.set('Connect the pad, then click Rescan')

    def select(self, _event=None):
        self.device, self.caps = self.available[self.selector.current()]
        self.previous = set()
        self.cancel_mapping()

    def learn(self, name):
        self.learning = name
        self.hint.set(f'Mapping {name}: release all controls, then press that area. Esc cancels.')
        self.armed = False
        self.paint()

    def cancel_mapping(self):
        self.learning = None
        self.hint.set('Click an area, release the pad, then press its matching control to map it.')
        self.paint()

    def save(self):
        try:
            CONFIG.write_text(json.dumps(self.mapping, indent=2))
        except OSError as error:
            self.hint.set(f'Mapping works for this session; could not save: {error}')

    def reset(self):
        self.mapping = {key: list(value) for key, value in DEFAULTS.items()}
        self.cancel_mapping()
        self.save()

    def poll(self):
        state = read_pad(self.device) if self.device is not None else None
        if state is not None:
            self.active = signals(state, self.caps)
            self.status.set(f'Connected • controller {self.device} • {self.caps.buttons} buttons • polling at ~60 Hz')
            if self.learning:
                if not self.active:
                    self.armed = True
                elif self.armed:
                    name = self.learning
                    # All signals from this press are alternatives for highlighting.
                    self.mapping[name] = sorted(self.active)
                    self.cancel_mapping()
                    self.hint.set(f'Saved {name}: {", ".join(self.mapping[name])}. Click another area to map it.')
                    self.save()
            if self.active != self.previous:
                added, removed = self.active-self.previous, self.previous-self.active
                parts = []
                if added:
                    parts.append('Pressed: ' + ', '.join(sorted(added)))
                if removed:
                    parts.append('Released: ' + ', '.join(sorted(removed)))
                self.event.set('   |   '.join(parts))
                self.previous = set(self.active)
            self.raw.set(f'Buttons: 0x{state.buttons:08X}   POV: {state.pov if state.pov != 65535 else "neutral"}\n'
                         f'X {state.x:5}   Y {state.y:5}   Z {state.z:5}   R {state.r:5}   U {state.u:5}   V {state.v:5}')
        else:
            self.active = set()
            if self.device is not None:
                self.status.set('Controller disconnected • reconnect it or click Rescan')
            self.raw.set('Waiting for controller input')
        for i, label in enumerate(self.button_labels):
            label.configure(bg='#167d56' if f'B{i+1}' in self.active else '#253044',
                            fg='white' if f'B{i+1}' in self.active else '#a9b8ca')
        self.paint()
        self.after(16, self.poll)


if __name__ == '__main__':
    if '--check' in sys.argv:
        for device, caps in devices():
            state = read_pad(device)
            print(f'Controller {device}: {caps.name}, VID={caps.mid:04X}, PID={caps.pid:04X}, buttons={caps.buttons}')
            print('Active:', ', '.join(sorted(signals(state, caps))) or 'none')
    else:
        app = Monitor()
        if '--smoke-test' in sys.argv:
            app.after(1200, app.destroy)
        app.mainloop()
