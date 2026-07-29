package com.wms.controller;
import com.google.zxing.*; import com.google.zxing.client.j2se.MatrixToImageWriter; import com.google.zxing.common.BitMatrix; import com.wms.common.*; import com.wms.model.entity.Item; import com.wms.repository.ItemRepository; import org.springframework.http.*; import org.springframework.web.bind.annotation.*; import java.io.*; import java.util.*;
@RestController @RequestMapping("/qrcodes") public class QrCodeController {
 private final ItemRepository items; public QrCodeController(ItemRepository items){this.items=items;}
 @GetMapping("/items/{code}") public ApiResponse<Map<String,Object>> data(@PathVariable String code)throws Exception{Item item=get(code);byte[] png=png(item.getCode());return ApiResponse.ok(Map.of("itemCode",item.getCode(),"itemName",item.getName(),"content",item.getCode(),"image","data:image/png;base64,"+Base64.getEncoder().encodeToString(png)));}
 @GetMapping(value="/items/{code}/png",produces=MediaType.IMAGE_PNG_VALUE) public ResponseEntity<byte[]> pngFile(@PathVariable String code)throws Exception{return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(png(get(code).getCode()));}
 private Item get(String code){return items.findByCode(code).orElseThrow(()->new BusinessException("物品不存在"));} private byte[] png(String content)throws Exception{BitMatrix matrix=new MultiFormatWriter().encode(content,BarcodeFormat.QR_CODE,280,280,Map.of(EncodeHintType.MARGIN,1));ByteArrayOutputStream output=new ByteArrayOutputStream();MatrixToImageWriter.writeToStream(matrix,"PNG",output);return output.toByteArray();}
}
