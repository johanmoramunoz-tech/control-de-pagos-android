'use strict';
const {createHash}=require('node:crypto');

const PRODUCT_ID='pagocheck_premium_unico';
const PACKAGE_NAME='cr.controles.pagos';
const TOKEN_RE=/^[A-Za-z0-9._:-]{20,4096}$/;

function hashToken(token){
 if(typeof token!=='string'||!TOKEN_RE.test(token))throw new Error('purchaseToken inválido');
 return createHash('sha256').update(token).digest('hex');
}
function assessPurchase(purchase){
 const state=purchase?.purchaseStateContext?.purchaseState;
 const matching=Array.isArray(purchase?.productLineItem) &&
  purchase.productLineItem.some(i=>i.productId===PRODUCT_ID &&
   i.productOfferDetails?.consumptionState!=='CONSUMPTION_STATE_CONSUMED');
 if(!matching)return {valid:false,reason:'wrong_product'};
 if(state==='PENDING')return {valid:false,reason:'pending'};
 if(state==='CANCELLED')return {valid:false,reason:'cancelled'};
 if(state!=='PURCHASED')return {valid:false,reason:'unknown_state'};
 return {valid:true,
  acknowledged:purchase.acknowledgementState==='ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED',
  reason:'purchased'};
}
function secureResponse(entitled,reason){
 return {premium:entitled===true,reason:String(reason),productId:PRODUCT_ID};
}
function parseAuthHeader(header){
 if(typeof header!=='string')return null;
 const match=/^Bearer ([A-Za-z0-9._-]{40,8192})$/.exec(header);
 return match?match[1]:null;
}
module.exports={PRODUCT_ID,PACKAGE_NAME,hashToken,assessPurchase,secureResponse,parseAuthHeader};