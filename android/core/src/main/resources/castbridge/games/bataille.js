// Bataille (démonstration) : ce que le téléphone dessine pour la table (voir play.html : GameUI.render(état, ui) est appelé à chaque changement d'état).
// État du jeu reçu dans `s.table` (GameRules.view) : { piles:{s1:26,s2:26}, table:[{player,card|null,up}], next:{player,up}|null, rounds, flips, battles, last:{winner,won,battle,cards}|null }.
window.GameUI=(function(){
  "use strict";
  var SUITS={C:"♣",D:"♦",H:"♥",S:"♠"};
  function el(tag,cls,text){ var e=document.createElement(tag); if(cls) e.className=cls; if(text!=null) e.textContent=text; return e; }
  function cardText(code){ return code.slice(0,-1)+SUITS[code.slice(-1)]; }
  function card(code,small){
    if(!code) return el("span","pc back"+(small?" small":""),"·");
    var red=code.slice(-1)==="D"||code.slice(-1)==="H";
    return el("span","pc"+(red?" red":"")+(small?" small":""),cardText(code));
  }
  function pile(name,count,active){
    var box=el("div","pile");
    box.style.cssText="flex:1;text-align:center;padding:8px;border-radius:12px;border:1px solid "+(active?"#33b5e5":"#343434")+";background:#1f1f1f";
    var stack=el("div"); stack.style.cssText="height:76px;display:flex;align-items:center;justify-content:center";
    if(count>0) stack.appendChild(card(null,false)); else stack.appendChild(el("span",null,"—"));
    box.appendChild(stack);
    box.appendChild(el("div",null,count+" carte"+(count>1?"s":"")));
    var n=el("div",null,name); n.style.cssText="color:#a8a8a8;font-size:14px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis"; box.appendChild(n);
    return box;
  }
  function lastLine(t,ui){
    var l=t.last; if(!l) return null;
    var cards=l.cards.map(function(c){ return ui.seatName(c.player)+" "+cardText(c.card); }).join(" · ");
    var e=el("div",null,(l.battle?"Bataille ! ":"")+ui.seatName(l.winner)+" gagne la levée ("+l.won+" cartes)");
    e.style.cssText="text-align:center;font-weight:700;margin:6px 0 2px";
    var sub=el("div",null,cards); sub.style.cssText="text-align:center;color:#a8a8a8;font-size:13px";
    var w=el("div"); w.appendChild(e); w.appendChild(sub); return w;
  }
  function render(s,ui){
    var root=ui.el; root.textContent="";
    var t=s.table;
    if(!t){ var wait=el("p",null,"La partie commence quand la TV la lance."); wait.style.cssText="text-align:center;color:#a8a8a8"; root.appendChild(wait); return; }
    var ids=Object.keys(t.piles), nextId=t.next?t.next.player:null;
    var piles=el("div"); piles.style.cssText="display:flex;gap:8px";
    ids.forEach(function(id){ piles.appendChild(pile(ui.seatName(id),t.piles[id],s.stage==="PLAYING"&&nextId===id)); });
    root.appendChild(piles);
    // la table : les cartes de la levée en cours, chacune sous le nom de celui qui l'a posée (les cartes cachées d'une bataille montrent leur dos)
    var cards=el("div"); cards.style.cssText="display:flex;flex-wrap:wrap;justify-content:center;min-height:84px;margin:10px 0 4px";
    t.table.forEach(function(p){
      var c=el("div"); c.style.cssText="display:flex;flex-direction:column;align-items:center;margin:0 2px";
      c.appendChild(card(p.up?p.card:null,true));
      var n=el("small",null,ui.seatName(p.player)); n.style.cssText="color:#a8a8a8;font-size:11px;max-width:54px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap"; c.appendChild(n);
      cards.appendChild(c);
    });
    root.appendChild(cards);
    var ll=lastLine(t,ui); if(ll) root.appendChild(ll);
    var counters=el("div",null,"Levée "+(t.rounds+1)+" · batailles : "+t.battles); counters.style.cssText="text-align:center;color:#a8a8a8;font-size:13px;margin-top:6px"; root.appendChild(counters);
    // le seul coup du jeu : retourner (ou poser face cachée pendant une bataille)
    if(s.stage==="PLAYING"&&ui.me){
      var mine=ui.legal.indexOf("flip")>=0;
      var b=el("button","big",mine?(t.next&&!t.next.up?"Poser une carte cachée":"Retourner ma carte"):"En attente de "+(nextId?ui.seatName(nextId):"l'adversaire")+"…");
      b.disabled=!mine; b.onclick=function(){ b.disabled=true; ui.send("flip"); };
      root.appendChild(b);
    }
  }
  return { render:render };
})();
