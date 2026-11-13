package com.openwormmc.client;

import com.openwormmc.entity.CElegansEntity;
import net.minecraft.client.model.*;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/** 12 articulated segments; each joint angle comes from the server (brain->muscle->body). UNCOMPILED in the build environment. */
public class CElegansRenderer extends EntityRenderer<CElegansEntity> {
    public static final EntityModelLayer LAYER = new EntityModelLayer(new Identifier("openwormmc", "c_elegans"), "main");
    private static final Identifier TEX = new Identifier("openwormmc", "textures/entity/c_elegans.png");
    private final ModelPart seg, head;

    public CElegansRenderer(EntityRendererFactory.Context ctx) { super(ctx); ModelPart root = ctx.getPart(LAYER); seg = root.getChild("seg"); head = root.getChild("head"); }

    public static TexturedModelData createModel() {
        ModelData md = new ModelData(); ModelPartData r = md.getRoot();
        r.addChild("seg", ModelPartBuilder.create().uv(0, 0).cuboid(-1.5f, -1.5f, -2.5f, 3, 3, 5), ModelTransform.NONE);
        r.addChild("head", ModelPartBuilder.create().uv(0, 8).cuboid(-1.0f, -1.0f, -2.0f, 2, 2, 4), ModelTransform.NONE);
        return TexturedModelData.of(md, 32, 16);
    }

    @Override public void render(CElegansEntity e, float yaw, float dt, MatrixStack m, VertexConsumerProvider vp, int light) {
        String s = e.getDataTracker().get(CElegansEntity.SHAPE); String[] a = s.isEmpty() ? new String[0] : s.split(",");
        m.push(); m.translate(0, 0.1, 0); m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180 - yaw));
        var vc = vp.getBuffer(RenderLayer.getEntityCutout(TEX)); float seglen = 1.0f / 12; double phi = 0, px = 0, pz = 0;
        for (int i = 0; i < 12; i++) {
            if (i > 0 && i - 1 < a.length) phi += Integer.parseInt(a[i - 1]) / 100.0;
            m.push(); m.translate(px, 0, pz); m.multiply(RotationAxis.POSITIVE_Y.rotation((float) phi)); m.scale(seglen * 16f / 5f * 0.0625f * 5f, 0.06f, seglen * 16f / 5f * 0.0625f * 5f);
            (i == 0 ? head : seg).render(m, vc, light, OverlayTexture.DEFAULT_UV); m.pop();
            px += Math.sin(phi) * seglen; pz += Math.cos(phi) * seglen;
        }
        m.pop(); super.render(e, yaw, dt, m, vp, light);
    }
    @Override public Identifier getTexture(CElegansEntity e) { return TEX; }
}
