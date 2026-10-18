#!/usr/bin/env node
'use strict';
// Génère icon.png (80x80) et largeIcon.png (130x130) de CastBridge-TV Web : couleur unie, sans dépendance (zlib + CRC32 maison).
//   node tools/webos/make-icons.js [dossier]      (défaut : webos/castbridge-tv-web)
// Compatible Node 8.

var fs = require('fs');
var path = require('path');
var zlib = require('zlib');

var COLOR = [0x0b, 0x5f, 0xa5];          // bleu CastBridge (#0b5fa5)
var SIZES = { 'icon.png': 80, 'largeIcon.png': 130 };

var table = (function () {
  var t = [];
  for (var n = 0; n < 256; n++) {
    var c = n;
    for (var k = 0; k < 8; k++) c = (c & 1) ? (0xedb88320 ^ (c >>> 1)) : (c >>> 1);
    t[n] = c >>> 0;
  }
  return t;
}());

function crc32(buf) {
  var c = 0xffffffff;
  for (var i = 0; i < buf.length; i++) c = table[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  var len = Buffer.alloc(4);
  len.writeUInt32BE(data.length, 0);
  var body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  var crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(body), 0);
  return Buffer.concat([len, body, crc]);
}

/** PNG RVB 8 bits, une seule couleur. */
function solidPng(size, rgb) {
  var ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(size, 0);
  ihdr.writeUInt32BE(size, 4);
  ihdr[8] = 8;      // profondeur
  ihdr[9] = 2;      // type de couleur : RVB
  ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
  var row = Buffer.alloc(1 + size * 3);       // filtre 0 puis les pixels
  for (var x = 0; x < size; x++) { row[1 + x * 3] = rgb[0]; row[2 + x * 3] = rgb[1]; row[3 + x * 3] = rgb[2]; }
  var raw = Buffer.concat(new Array(size).fill(row));
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))
  ]);
}

function main() {
  var dir = process.argv[2] || path.join(__dirname, '..', '..', 'webos', 'castbridge-tv-web');
  Object.keys(SIZES).forEach(function (name) {
    var file = path.join(dir, name);
    fs.writeFileSync(file, solidPng(SIZES[name], COLOR));
    console.log(file + ' : ' + SIZES[name] + 'x' + SIZES[name]);
  });
}

if (require.main === module) main();
module.exports = { solidPng: solidPng, crc32: crc32 };
